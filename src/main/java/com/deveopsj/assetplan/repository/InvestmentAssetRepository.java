package com.deveopsj.assetplan.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.deveopsj.assetplan.entity.InvestmentAsset;

public interface InvestmentAssetRepository extends JpaRepository<InvestmentAsset, Long> {

    Optional<InvestmentAsset> findByMarketAndSymbol(String market, String symbol);
}
