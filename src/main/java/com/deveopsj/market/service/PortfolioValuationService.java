package com.deveopsj.market.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.deveopsj.assetplan.entity.AssetTrade;
import com.deveopsj.assetplan.entity.AssetTrade.TradeType;
import com.deveopsj.assetplan.repository.AssetTradeRepository;
import com.deveopsj.market.dto.MarketPriceQuote;
import com.deveopsj.market.dto.MonthlyPortfolioPosition;
import com.deveopsj.market.dto.MonthlyPortfolioSummary;
import com.deveopsj.market.dto.PortfolioPosition;
import com.deveopsj.market.dto.PortfolioSummary;
import com.deveopsj.member.entity.Member;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PortfolioValuationService {

    private static final int CALCULATION_SCALE = 12;

    private final AssetTradeRepository assetTradeRepository;
    private final KisMarketPriceService kisMarketPriceService;

    public PortfolioSummary getPortfolio(Member member) {
        List<AssetTrade> trades = assetTradeRepository
                .findByAssetPlanMemberMemberIdOrderByTradeDateAscIdAsc(member.getMemberId());
        Map<PositionKey, PositionAccumulator> accumulators = new LinkedHashMap<>();
        for (AssetTrade trade : trades) {
            PositionKey key = new PositionKey(
                    trade.getAssetPlan().getId(), trade.getInvestmentAsset().getId());
            accumulators.computeIfAbsent(key, ignored -> new PositionAccumulator(trade))
                    .apply(trade);
        }

        List<PortfolioPosition> positions = new ArrayList<>();
        for (PositionAccumulator accumulator : accumulators.values()) {
            if (accumulator.quantity.signum() > 0) {
                positions.add(toPosition(accumulator));
            }
        }
        long totalCost = positions.stream().mapToLong(PortfolioPosition::remainingCostKrw).sum();
        long totalValuation = positions.stream()
                .filter(PortfolioPosition::quoteAvailable)
                .mapToLong(PortfolioPosition::valuationAmountKrw)
                .sum();
        long quotedCost = positions.stream()
                .filter(PortfolioPosition::quoteAvailable)
                .mapToLong(PortfolioPosition::remainingCostKrw)
                .sum();
        return new PortfolioSummary(
                List.copyOf(positions),
                totalCost,
                quotedCost,
                totalValuation,
                totalValuation - quotedCost);
    }

    public MonthlyPortfolioSummary getMonthlyPortfolio(Member member, YearMonth selectedMonth) {
        List<AssetTrade> trades = assetTradeRepository
                .findByAssetPlanMemberMemberIdAndTradeDateLessThanEqualOrderByTradeDateAscIdAsc(
                        member.getMemberId(), selectedMonth.atEndOfMonth());
        Map<PositionKey, PositionAccumulator> accumulators = new LinkedHashMap<>();
        long monthlyBuyAmount = 0;
        long monthlySellAmount = 0;
        for (AssetTrade trade : trades) {
            PositionKey key = new PositionKey(
                    trade.getAssetPlan().getId(), trade.getInvestmentAsset().getId());
            accumulators.computeIfAbsent(key, ignored -> new PositionAccumulator(trade))
                    .apply(trade);
            if (YearMonth.from(trade.getTradeDate()).equals(selectedMonth)) {
                if (trade.getTradeType() == TradeType.BUY) {
                    monthlyBuyAmount = Math.addExact(
                            monthlyBuyAmount, trade.getSettlementAmountKrw());
                } else {
                    monthlySellAmount = Math.addExact(
                            monthlySellAmount, trade.getSettlementAmountKrw());
                }
            }
        }

        List<MonthlyPortfolioPosition> positions = accumulators.values().stream()
                .filter(accumulator -> accumulator.quantity.signum() > 0)
                .map(PositionAccumulator::monthlyPosition)
                .toList();
        long monthEndCost = positions.stream()
                .mapToLong(MonthlyPortfolioPosition::remainingCostKrw)
                .sum();
        return new MonthlyPortfolioSummary(
                selectedMonth,
                monthlyBuyAmount,
                monthlySellAmount,
                Math.subtractExact(monthlyBuyAmount, monthlySellAmount),
                monthEndCost,
                positions);
    }

    private PortfolioPosition toPosition(PositionAccumulator accumulator) {
        BigDecimal averageUnitPrice = accumulator.nativeCost
                .divide(accumulator.quantity, 4, RoundingMode.HALF_UP);
        long remainingCostKrw = accumulator.krwCost.setScale(0, RoundingMode.HALF_UP).longValue();
        try {
            MarketPriceQuote quote = kisMarketPriceService.getQuote(accumulator.investmentAssetId);
            BigDecimal valuation = quote.currentPrice()
                    .multiply(accumulator.quantity)
                    .multiply(accumulator.exchangeRate);
            long valuationKrw = valuation.setScale(0, RoundingMode.HALF_UP).longValueExact();
            long profitLossKrw = Math.subtractExact(valuationKrw, remainingCostKrw);
            BigDecimal returnRate = remainingCostKrw == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(profitLossKrw)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(remainingCostKrw), 2, RoundingMode.HALF_UP);
            return accumulator.position(
                    averageUnitPrice, remainingCostKrw, quote.currentPrice(),
                    valuationKrw, profitLossKrw, returnRate,
                    quote.fetchedAt(), quote.cached(), quote.stale(), null);
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException e) {
            String errorMessage = e.getMessage() == null
                    ? "현재가를 계산하지 못했습니다." : e.getMessage();
            return accumulator.position(
                    averageUnitPrice, remainingCostKrw, null,
                    null, null, null, null, false, false, errorMessage);
        }
    }

    private record PositionKey(Long assetPlanId, Long investmentAssetId) {
    }

    private static final class PositionAccumulator {

        private final Long assetPlanId;
        private final String planName;
        private final Long investmentAssetId;
        private final String symbol;
        private final String assetName;
        private final String market;
        private final String currency;
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal nativeCost = BigDecimal.ZERO;
        private BigDecimal krwCost = BigDecimal.ZERO;
        private BigDecimal exchangeRate = BigDecimal.ONE;

        private PositionAccumulator(AssetTrade trade) {
            this.assetPlanId = trade.getAssetPlan().getId();
            this.planName = trade.getAssetPlan().getPlanName() == null
                    || trade.getAssetPlan().getPlanName().isBlank()
                    ? trade.getAssetPlan().getGoal().getTitle()
                    : trade.getAssetPlan().getPlanName();
            this.investmentAssetId = trade.getInvestmentAsset().getId();
            this.symbol = trade.getInvestmentAsset().getSymbol();
            this.assetName = trade.getInvestmentAsset().getAssetName();
            this.market = trade.getInvestmentAsset().getMarket();
            this.currency = trade.getInvestmentAsset().getCurrency();
        }

        private void apply(AssetTrade trade) {
            exchangeRate = trade.getExchangeRate();
            if (trade.getTradeType() == TradeType.BUY) {
                quantity = quantity.add(trade.getQuantity());
                nativeCost = nativeCost.add(trade.getUnitPrice().multiply(trade.getQuantity()));
                krwCost = krwCost.add(BigDecimal.valueOf(trade.getSettlementAmountKrw()));
                return;
            }
            if (quantity.signum() <= 0 || trade.getQuantity().compareTo(quantity) >= 0) {
                quantity = BigDecimal.ZERO;
                nativeCost = BigDecimal.ZERO;
                krwCost = BigDecimal.ZERO;
                return;
            }
            BigDecimal remainingRatio = quantity.subtract(trade.getQuantity())
                    .divide(quantity, CALCULATION_SCALE, RoundingMode.HALF_UP);
            quantity = quantity.subtract(trade.getQuantity());
            nativeCost = nativeCost.multiply(remainingRatio);
            krwCost = krwCost.multiply(remainingRatio);
        }

        private PortfolioPosition position(BigDecimal averageUnitPrice, long remainingCostKrw,
                BigDecimal currentPrice, Long valuationAmountKrw, Long profitLossKrw,
                BigDecimal returnRate, java.time.LocalDateTime quoteFetchedAt,
                boolean cachedQuote, boolean staleQuote, String quoteError) {
            return new PortfolioPosition(
                    assetPlanId, planName, investmentAssetId, symbol, assetName, market, currency,
                    quantity.stripTrailingZeros(), averageUnitPrice, remainingCostKrw,
                    currentPrice, exchangeRate, valuationAmountKrw, profitLossKrw,
                    returnRate, quoteFetchedAt, cachedQuote, staleQuote, quoteError);
        }

        private MonthlyPortfolioPosition monthlyPosition() {
            BigDecimal averageUnitPrice = nativeCost
                    .divide(quantity, 4, RoundingMode.HALF_UP);
            long remainingCostKrw = krwCost.setScale(0, RoundingMode.HALF_UP).longValueExact();
            return new MonthlyPortfolioPosition(
                    assetPlanId, planName, investmentAssetId, symbol, assetName, market, currency,
                    quantity.stripTrailingZeros(), averageUnitPrice, remainingCostKrw);
        }
    }
}
