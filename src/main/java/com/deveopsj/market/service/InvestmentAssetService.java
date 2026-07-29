package com.deveopsj.market.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deveopsj.assetplan.repository.InvestmentAssetRepository;
import com.deveopsj.market.dto.InvestmentAssetSearchItem;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InvestmentAssetService {

    private final InvestmentAssetRepository investmentAssetRepository;

    @Transactional(readOnly = true)
    public List<InvestmentAssetSearchItem> search(String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.length() < 2) {
            return List.of();
        }
        return investmentAssetRepository
                .findTop20ByActiveTrueAndSymbolContainingIgnoreCaseOrActiveTrueAndAssetNameContainingIgnoreCaseOrderByAssetNameAsc(
                        normalizedKeyword, normalizedKeyword)
                .stream()
                .map(InvestmentAssetSearchItem::from)
                .toList();
    }
}
