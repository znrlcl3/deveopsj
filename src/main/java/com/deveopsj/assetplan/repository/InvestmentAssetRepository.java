package com.deveopsj.assetplan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.deveopsj.assetplan.entity.InvestmentAsset;

public interface InvestmentAssetRepository extends JpaRepository<InvestmentAsset, Long> {

    Optional<InvestmentAsset> findByMarketAndSymbol(String market, String symbol);

    Optional<InvestmentAsset> findByIdAndActiveTrue(Long id);

    List<InvestmentAsset> findTop20ByActiveTrueAndSymbolContainingIgnoreCaseOrActiveTrueAndAssetNameContainingIgnoreCaseOrderByAssetNameAsc(
            String symbol, String assetName);
}
