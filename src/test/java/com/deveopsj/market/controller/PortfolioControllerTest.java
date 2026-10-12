package com.deveopsj.market.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

import com.deveopsj.market.dto.ExchangeRateQuote;
import com.deveopsj.market.dto.PortfolioSummary;
import com.deveopsj.market.service.KisExchangeRateService;
import com.deveopsj.market.service.PortfolioValuationService;
import com.deveopsj.member.entity.Member;

@ExtendWith(MockitoExtension.class)
class PortfolioControllerTest {

    @Mock
    private PortfolioValuationService portfolioValuationService;

    @Mock
    private KisExchangeRateService kisExchangeRateService;

    @Test
    void 보유자산화면에_현재_원달러환율을_제공한다() {
        Member member = new Member();
        PortfolioSummary summary = new PortfolioSummary(List.of(), 0L, 0L, 0L, 0L);
        ExchangeRateQuote quote = new ExchangeRateQuote(
                "USD", new BigDecimal("1400.25"), LocalDateTime.now(), false, false);
        when(portfolioValuationService.getPortfolio(member)).thenReturn(summary);
        when(kisExchangeRateService.getKrwRate("USD")).thenReturn(quote);
        PortfolioController controller = new PortfolioController(
                portfolioValuationService, kisExchangeRateService);
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.portfolio(model, member);

        assertThat(view).isEqualTo("portfolio/list");
        assertThat(model.get("summary")).isSameAs(summary);
        assertThat(model.get("usdKrwRate")).isSameAs(quote);
    }

    @Test
    void 환율조회실패가_보유자산화면을_중단하지않는다() {
        Member member = new Member();
        PortfolioSummary summary = new PortfolioSummary(List.of(), 0L, 0L, 0L, 0L);
        when(portfolioValuationService.getPortfolio(member)).thenReturn(summary);
        when(kisExchangeRateService.getKrwRate("USD"))
                .thenThrow(new IllegalStateException("환율 조회 실패"));
        PortfolioController controller = new PortfolioController(
                portfolioValuationService, kisExchangeRateService);
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.portfolio(model, member);

        assertThat(view).isEqualTo("portfolio/list");
        assertThat(model.get("summary")).isSameAs(summary);
        assertThat(model.get("exchangeRateError")).isEqualTo("환율 조회 실패");
    }
}
