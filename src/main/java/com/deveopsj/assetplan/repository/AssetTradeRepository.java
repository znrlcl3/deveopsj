package com.deveopsj.assetplan.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.deveopsj.assetplan.entity.AssetTrade;
import com.deveopsj.assetplan.entity.AssetTrade.TradeType;

public interface AssetTradeRepository extends JpaRepository<AssetTrade, Long> {

    List<AssetTrade> findByAssetPlanMemberMemberIdOrderByTradeDateDescIdDesc(Long memberId);

    List<AssetTrade> findByAssetPlanMemberMemberIdAndTradeDateBetweenOrderByTradeDateDescIdDesc(
            Long memberId, LocalDate startDate, LocalDate endDate);

    Optional<AssetTrade> findByIdAndAssetPlanMemberMemberId(Long id, Long memberId);

    @Query("""
            SELECT SUM(t.settlementAmountKrw)
            FROM AssetTrade t
            WHERE t.assetPlan.id = :planId
              AND t.tradeType = :tradeType
              AND t.tradeDate BETWEEN :startDate AND :endDate
            """)
    Long getTotalSettlementByPlanIdAndTypeAndTradeDateBetween(
            @Param("planId") Long planId,
            @Param("tradeType") TradeType tradeType,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
            SELECT SUM(t.settlementAmountKrw)
            FROM AssetTrade t
            WHERE t.assetPlan.id = :planId
              AND t.tradeType = :tradeType
            """)
    Long getTotalSettlementByPlanIdAndType(
            @Param("planId") Long planId,
            @Param("tradeType") TradeType tradeType);
}
