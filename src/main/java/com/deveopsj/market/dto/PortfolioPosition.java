package com.deveopsj.market.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PortfolioPosition(
        Long assetPlanId,
        String planName,
        Long investmentAssetId,
        String symbol,
        String assetName,
        String market,
        String currency,
        BigDecimal quantity,
        BigDecimal averageUnitPrice,
        Long remainingCostKrw,
        BigDecimal currentPrice,
        BigDecimal appliedExchangeRate,
        LocalDateTime exchangeRateFetchedAt,
        boolean cachedExchangeRate,
        boolean staleExchangeRate,
        String exchangeRateWarning,
        Long valuationAmountKrw,
        Long profitLossKrw,
        BigDecimal returnRate,
        LocalDateTime quoteFetchedAt,
        boolean cachedQuote,
        boolean staleQuote,
        String quoteError, BigDecimal remainingCostNative, Long referenceCostKrw, boolean referenceCostEstimated) {

    public PortfolioPosition(Long assetPlanId,String planName,Long investmentAssetId,String symbol,String assetName,String market,String currency,
        BigDecimal quantity,BigDecimal averageUnitPrice,Long remainingCostKrw,BigDecimal currentPrice,BigDecimal appliedExchangeRate,
        LocalDateTime exchangeRateFetchedAt,boolean cachedExchangeRate,boolean staleExchangeRate,String exchangeRateWarning,
        Long valuationAmountKrw,Long profitLossKrw,BigDecimal returnRate,LocalDateTime quoteFetchedAt,boolean cachedQuote,boolean staleQuote,String quoteError) {
        this(assetPlanId,planName,investmentAssetId,symbol,assetName,market,currency,quantity,averageUnitPrice,remainingCostKrw,currentPrice,appliedExchangeRate,
            exchangeRateFetchedAt,cachedExchangeRate,staleExchangeRate,exchangeRateWarning,valuationAmountKrw,profitLossKrw,returnRate,quoteFetchedAt,cachedQuote,staleQuote,quoteError,
            averageUnitPrice.multiply(quantity),remainingCostKrw,false);
    }
    public BigDecimal nativeProfitLoss() { return currentPrice==null || remainingCostNative==null ? null : currentPrice.multiply(quantity).subtract(remainingCostNative); }
    public BigDecimal nativeReturnRate() {
        if(nativeProfitLoss()==null || remainingCostNative.signum()<=0) return null;
        return nativeProfitLoss().multiply(BigDecimal.valueOf(100)).divide(remainingCostNative,2,java.math.RoundingMode.HALF_UP);
    }
    public Long fxProfitLossKrw() { return valuationAmountKrw==null || referenceCostKrw==null ? null : Math.subtractExact(valuationAmountKrw,referenceCostKrw); }
    public BigDecimal fxReturnRate() { return fxProfitLossKrw()==null || referenceCostKrw==0 ? null : BigDecimal.valueOf(fxProfitLossKrw()).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(referenceCostKrw),2,java.math.RoundingMode.HALF_UP); }
    public boolean quoteAvailable() {
        return quoteError == null;
    }
}
