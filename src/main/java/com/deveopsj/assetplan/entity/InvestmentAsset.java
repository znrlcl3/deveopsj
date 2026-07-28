package com.deveopsj.assetplan.entity;

import com.deveopsj.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "investment_asset",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_investment_asset_market_symbol",
                columnNames = {"market", "symbol"}))
@Getter
@Setter
public class InvestmentAsset extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String symbol;

    @Column(nullable = false, length = 100)
    private String assetName;

    @Column(nullable = false, length = 20)
    private String market;

    @Column(nullable = false, length = 20)
    private String assetClass;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private boolean active = true;
}
