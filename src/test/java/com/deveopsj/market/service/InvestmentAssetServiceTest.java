package com.deveopsj.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.deveopsj.assetplan.entity.InvestmentAsset;
import com.deveopsj.assetplan.repository.InvestmentAssetRepository;

@ExtendWith(MockitoExtension.class)
class InvestmentAssetServiceTest {

    @Mock
    private InvestmentAssetRepository repository;

    @Test
    void 두글자미만은_검색하지않는다() {
        InvestmentAssetService service = new InvestmentAssetService(repository);

        assertThat(service.search("A")).isEmpty();
        verify(repository, never())
                .findTop20ByActiveTrueAndSymbolContainingIgnoreCaseOrActiveTrueAndAssetNameContainingIgnoreCaseOrderByAssetNameAsc(
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 종목코드와_종목명_검색결과를_화면DTO로_반환한다() {
        InvestmentAsset asset = new InvestmentAsset();
        asset.setId(3L);
        asset.setSymbol("AAPL");
        asset.setAssetName("Apple Inc.");
        asset.setMarket("NASDAQ");
        asset.setAssetClass("STOCK");
        asset.setCurrency("USD");
        when(repository
                .findTop20ByActiveTrueAndSymbolContainingIgnoreCaseOrActiveTrueAndAssetNameContainingIgnoreCaseOrderByAssetNameAsc(
                        "AAPL", "AAPL"))
                .thenReturn(List.of(asset));
        InvestmentAssetService service = new InvestmentAssetService(repository);

        assertThat(service.search(" AAPL ")).singleElement()
                .satisfies(item -> {
                    assertThat(item.id()).isEqualTo(3L);
                    assertThat(item.symbol()).isEqualTo("AAPL");
                    assertThat(item.currency()).isEqualTo("USD");
                });
    }
}
