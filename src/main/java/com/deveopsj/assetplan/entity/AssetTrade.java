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

    @Column(precision=19,scale=6) private BigDecimal referenceExchangeRate;
    @Column(precision=19,scale=4) private BigDecimal foreignGross;
    @Column(precision=19,scale=4) private BigDecimal foreignFee;
    @Column(precision=19,scale=4) private BigDecimal foreignTax;
    @Column(precision=19,scale=4) private BigDecimal foreignSettlement;

    @Column(length=2000)
    private String importDetails;

    @Column(length=64)
    private String importGroupKey;

    @Column(length = 64, unique = true)
    private String importKey;

    @Column(nullable = false)
    private boolean historyOnly;

    public enum TradeType {
        BUY, SELL, SNAPSHOT
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

    @Column(precision = 19, scale = 6)
    private BigDecimal exchangeRate;

    @Column
    private Long feeKrw;

    @Column
    private Long taxKrw;

    @Column
    private Long settlementAmountKrw;

    @Column(length = 200)
    private String memo;
}
