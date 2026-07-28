package com.deveopsj.assetplan.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.deveopsj.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "asset_trade", indexes = {
        @Index(name = "idx_asset_trade_plan_date", columnList = "plan_id, trade_date"),
        @Index(name = "idx_asset_trade_asset_date", columnList = "asset_id, trade_date")
})
@Getter
@Setter
public class AssetTrade extends BaseEntity {

    public enum TradeType {
        BUY, SELL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private AssetPlan assetPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private InvestmentAsset investmentAsset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TradeType tradeType;

    @Column(nullable = false)
    private LocalDate tradeDate;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal exchangeRate;

    @Column(nullable = false)
    private Long feeKrw;

    @Column(nullable = false)
    private Long taxKrw;

    @Column(nullable = false)
    private Long settlementAmountKrw;

    @Column(length = 200)
    private String memo;
}
