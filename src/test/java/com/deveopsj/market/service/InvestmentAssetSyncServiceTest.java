package com.deveopsj.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.deveopsj.assetplan.entity.InvestmentAsset;
import com.deveopsj.assetplan.repository.InvestmentAssetRepository;
import com.deveopsj.market.dto.InvestmentAssetSyncResult;
import com.deveopsj.market.dto.KrxMarket;
import com.deveopsj.market.dto.KrxStockItem;

@ExtendWith(MockitoExtension.class)
class InvestmentAssetSyncServiceTest {

    @Mock
    private InvestmentAssetRepository repository;

    @Test
    void 국내ETF를_원화_종목마스터로_저장한다() {
        when(repository.findAll()).thenReturn(List.of());
        InvestmentAssetSyncService service = new InvestmentAssetSyncService(repository);
        KrxStockItem stock = new KrxStockItem(
                null, "402970", "ACE 미국배당다우존스", "ACE 미국배당다우존스",
                null, null, "ETF", "ETF", "ETF", "1000");

        InvestmentAssetSyncResult result = service.synchronize(List.of(stock), KrxMarket.ETF);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<InvestmentAsset>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(captor.capture());
        InvestmentAsset saved = captor.getValue().iterator().next();
        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(saved.getSymbol()).isEqualTo("402970");
        assertThat(saved.getAssetClass()).isEqualTo("ETF");
        assertThat(saved.getCurrency()).isEqualTo("KRW");
    }

    @Test
    void 기존미국종목은_중복생성하지않고_갱신한다() {
        InvestmentAsset existing = new InvestmentAsset();
        existing.setMarket("NASDAQ");
        existing.setSymbol("AAPL");
        existing.setAssetName("Old Apple");
        existing.setAssetClass("STOCK");
        existing.setCurrency("USD");
        when(repository.findAll()).thenReturn(List.of(existing));
        InvestmentAssetSyncService service = new InvestmentAssetSyncService(repository);
        KrxStockItem stock = new KrxStockItem(
                null, "AAPL", "Apple Inc.", "Apple Inc.",
                null, null, "NASDAQ", "STOCK", "STOCK", null);

        InvestmentAssetSyncResult result = service.synchronize(List.of(stock), KrxMarket.US);

        assertThat(result.createdCount()).isZero();
        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(existing.getAssetName()).isEqualTo("Apple Inc.");
        assertThat(existing.getCurrency()).isEqualTo("USD");
    }
}
