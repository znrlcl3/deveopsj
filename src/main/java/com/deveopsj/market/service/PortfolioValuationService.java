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
import com.deveopsj.market.dto.ExchangeRateQuote;
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
    private final KisExchangeRateService kisExchangeRateService;

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
        Long totalCost = positions.stream().anyMatch(p -> p.remainingCostKrw()==null) ? null : positions.stream().mapToLong(PortfolioPosition::remainingCostKrw).sum();
        long totalValuation = positions.stream()
                .filter(PortfolioPosition::quoteAvailable)
                .mapToLong(PortfolioPosition::valuationAmountKrw)
                .sum();
        long quotedCost = positions.stream()
                .filter(p -> p.quoteAvailable() && p.remainingCostKrw()!=null)
                .mapToLong(PortfolioPosition::remainingCostKrw)
                .sum();
        return new PortfolioSummary(
                List.copyOf(positions),
                totalCost,
                quotedCost,
                totalValuation,
                totalCost==null ? null : totalValuation - quotedCost);
    }

    public MonthlyPortfolioSummary getMonthlyPortfolio(Member member, YearMonth selectedMonth) {
        List<AssetTrade> trades = assetTradeRepository
                .findByAssetPlanMemberMemberIdAndTradeDateLessThanEqualOrderByTradeDateAscIdAsc(
                        member.getMemberId(), selectedMonth.atEndOfMonth());
        Map<PositionKey, PositionAccumulator> accumulators = new LinkedHashMap<>();
        Long monthlyBuyAmount = 0L;
        Long monthlySellAmount = 0L;
        for (AssetTrade trade : trades) {
            PositionKey key = new PositionKey(
                    trade.getAssetPlan().getId(), trade.getInvestmentAsset().getId());
            accumulators.computeIfAbsent(key, ignored -> new PositionAccumulator(trade))
                    .apply(trade);
            if (YearMonth.from(trade.getTradeDate()).equals(selectedMonth)) {
                if (trade.getTradeType() == TradeType.BUY) {
                    monthlyBuyAmount = addKnown(
                            monthlyBuyAmount, trade.getSettlementAmountKrw());
                } else if (trade.getTradeType() == TradeType.SELL) {
                    monthlySellAmount = addKnown(
                            monthlySellAmount, trade.getSettlementAmountKrw());
                }
            }
        }

        List<MonthlyPortfolioPosition> positions = accumulators.values().stream()
                .filter(accumulator -> accumulator.quantity.signum() > 0)
                .map(PositionAccumulator::monthlyPosition)
                .toList();
        Long monthEndCost = positions.stream().anyMatch(p -> p.remainingCostKrw()==null) ? null : positions.stream()
                .mapToLong(MonthlyPortfolioPosition::remainingCostKrw)
                .sum();
        return new MonthlyPortfolioSummary(
                selectedMonth,
                monthlyBuyAmount,
                monthlySellAmount,
                monthlyBuyAmount==null || monthlySellAmount==null ? null : Math.subtractExact(monthlyBuyAmount, monthlySellAmount),
                monthEndCost,
                positions);
    }

    private static Long addKnown(Long a,Long b) { return a==null || b==null ? null : Math.addExact(a,b); }

    private PortfolioPosition toPosition(PositionAccumulator accumulator) {
        BigDecimal averageUnitPrice = accumulator.nativeCost
                .divide(accumulator.quantity, 4, RoundingMode.HALF_UP);
        Long remainingCostKrw = accumulator.krwCost==null ? null : accumulator.krwCost.setScale(0, RoundingMode.HALF_UP).longValueExact();
        try {
            MarketPriceQuote quote = kisMarketPriceService.getQuote(accumulator.investmentAssetId);
            ExchangeRateQuote exchangeRateQuote;
            String exchangeRateWarning = null;
            try {
                exchangeRateQuote = kisExchangeRateService.getKrwRate(accumulator.currency);
            } catch (IllegalArgumentException | IllegalStateException e) {
                if(accumulator.exchangeRate==null || accumulator.krwCost==null) return accumulator.position(averageUnitPrice,remainingCostKrw,quote.currentPrice(),null,
                    "현재 환율을 조회할 수 없습니다.",null,null,null,quote.fetchedAt(),quote.cached(),quote.stale(),"현재 환율 조회 불가");
                exchangeRateQuote = new ExchangeRateQuote(
                        accumulator.currency, accumulator.exchangeRate, null, false, true);
                exchangeRateWarning = "실시간 환율 조회 실패로 최근 매매 환율을 적용했습니다.";
            }
            BigDecimal valuation = quote.currentPrice()
                    .multiply(accumulator.quantity)
                    .multiply(exchangeRateQuote.krwRate());
            long valuationKrw = valuation.setScale(0, RoundingMode.HALF_UP).longValueExact();
            Long profitLossKrw = remainingCostKrw==null ? null : Math.subtractExact(valuationKrw, remainingCostKrw);
            BigDecimal returnRate = remainingCostKrw==null ? null : remainingCostKrw == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(profitLossKrw)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(remainingCostKrw), 2, RoundingMode.HALF_UP);
            return accumulator.position(
                    averageUnitPrice, remainingCostKrw, quote.currentPrice(),
                    exchangeRateQuote, exchangeRateWarning,
                    valuationKrw, profitLossKrw, returnRate,
                    quote.fetchedAt(), quote.cached(), quote.stale(), null);
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException e) {
            String errorMessage = e.getMessage() == null
                    ? "현재가를 계산하지 못했습니다." : e.getMessage();
            return accumulator.position(
                    averageUnitPrice, remainingCostKrw, null,
                    null, null,
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
        private BigDecimal referenceCost = BigDecimal.ZERO;
        private boolean referenceEstimated;

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
            if (trade.isHistoryOnly()) return;
            exchangeRate = trade.getExchangeRate();
            BigDecimal tradeReference = trade.getSettlementAmountKrw()!=null ? BigDecimal.valueOf(trade.getSettlementAmountKrw())
                : trade.getReferenceExchangeRate()!=null && trade.getReferenceExchangeRate().signum()>0 && trade.getForeignSettlement()!=null
                    ? trade.getForeignSettlement().multiply(trade.getReferenceExchangeRate()) : null;
            if (trade.getTradeType() == TradeType.SNAPSHOT) {
                referenceCost=tradeReference;referenceEstimated=trade.getSettlementAmountKrw()==null;
                quantity = trade.getQuantity();
                nativeCost = trade.getUnitPrice().multiply(quantity);
                krwCost = trade.getSettlementAmountKrw()==null ? null : BigDecimal.valueOf(trade.getSettlementAmountKrw());
                return;
            }
            if (trade.getTradeType() == TradeType.BUY) {
                referenceCost=referenceCost==null || tradeReference==null ? null : referenceCost.add(tradeReference);
                referenceEstimated |= trade.getSettlementAmountKrw()==null;
                quantity = quantity.add(trade.getQuantity());
                nativeCost = nativeCost.add(trade.getForeignSettlement()!=null ? trade.getForeignSettlement() : trade.getUnitPrice().multiply(trade.getQuantity()));
                krwCost = krwCost==null || trade.getSettlementAmountKrw()==null ? null : krwCost.add(BigDecimal.valueOf(trade.getSettlementAmountKrw()));
                return;
            }
            if (quantity.signum() <= 0 || trade.getQuantity().compareTo(quantity) >= 0) {
                referenceCost=BigDecimal.ZERO;referenceEstimated=false;
                quantity = BigDecimal.ZERO;
                nativeCost = BigDecimal.ZERO;
                krwCost = BigDecimal.ZERO;
                return;
            }
            BigDecimal remainingRatio = quantity.subtract(trade.getQuantity())
                    .divide(quantity, CALCULATION_SCALE, RoundingMode.HALF_UP);
            quantity = quantity.subtract(trade.getQuantity());
            nativeCost = nativeCost.multiply(remainingRatio);
            krwCost = krwCost==null ? null : krwCost.multiply(remainingRatio);
            referenceCost=referenceCost==null ? null : referenceCost.multiply(remainingRatio);
        }

        private PortfolioPosition position(BigDecimal averageUnitPrice, Long remainingCostKrw,
                BigDecimal currentPrice, ExchangeRateQuote exchangeRateQuote,
                String exchangeRateWarning, Long valuationAmountKrw, Long profitLossKrw,
                BigDecimal returnRate, java.time.LocalDateTime quoteFetchedAt,
                boolean cachedQuote, boolean staleQuote, String quoteError) {
            BigDecimal appliedExchangeRate = exchangeRateQuote == null
                    ? exchangeRate : exchangeRateQuote.krwRate();
            return new PortfolioPosition(
                    assetPlanId, planName, investmentAssetId, symbol, assetName, market, currency,
                    quantity.stripTrailingZeros(), averageUnitPrice, remainingCostKrw,
                    currentPrice, appliedExchangeRate,
                    exchangeRateQuote == null ? null : exchangeRateQuote.fetchedAt(),
                    exchangeRateQuote != null && exchangeRateQuote.cached(),
                    exchangeRateQuote != null && exchangeRateQuote.stale(), exchangeRateWarning,
                    valuationAmountKrw, profitLossKrw,
                    returnRate, quoteFetchedAt, cachedQuote, staleQuote, quoteError,nativeCost,referenceCost==null?null:referenceCost.setScale(0,RoundingMode.HALF_UP).longValueExact(),referenceEstimated);
        }

        private MonthlyPortfolioPosition monthlyPosition() {
            BigDecimal averageUnitPrice = nativeCost
                    .divide(quantity, 4, RoundingMode.HALF_UP);
            Long remainingCostKrw = krwCost==null ? null : krwCost.setScale(0, RoundingMode.HALF_UP).longValueExact();
            return new MonthlyPortfolioPosition(
                    assetPlanId, planName, investmentAssetId, symbol, assetName, market, currency,
                    quantity.stripTrailingZeros(), averageUnitPrice, remainingCostKrw);
        }
    }
}
