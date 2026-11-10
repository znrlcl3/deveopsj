package com.deveopsj.assetplan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.deveopsj.assetplan.entity.InvestmentAsset;

public interface InvestmentAssetRepository extends JpaRepository<InvestmentAsset, Long> {
    List<InvestmentAsset> findByActiveTrueAndCurrency(String currency);

    List<InvestmentAsset> findByActiveTrueAndAssetNameAndCurrency(String assetName, String currency);

    List<InvestmentAsset> findByActiveTrueAndSymbolAndCurrency(String symbol, String currency);

    Optional<InvestmentAsset> findByMarketAndSymbol(String market, String symbol);

    Optional<InvestmentAsset> findByIdAndActiveTrue(Long id);

    List<InvestmentAsset> findTop20ByActiveTrueAndSymbolContainingIgnoreCaseOrActiveTrueAndAssetNameContainingIgnoreCaseOrderByAssetNameAsc(
            String symbol, String assetName);
}
