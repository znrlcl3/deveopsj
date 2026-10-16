package com.deveopsj.spending.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.deveopsj.common.service.DataInputService;
import com.deveopsj.member.entity.Member;
import com.deveopsj.spending.dto.RecurringExpenseOccurrence;
import com.deveopsj.spending.entity.DailySpending;
import com.deveopsj.spending.entity.RecurringExpense;
import com.deveopsj.spending.service.RecurringExpenseService;
import com.deveopsj.spending.service.SpendingExcelImportService;
import com.deveopsj.spending.service.SpendingService;
import com.deveopsj.spending.service.CardSpendingImportService;
import com.deveopsj.spending.dto.CardCompany;
import com.deveopsj.spending.dto.CardImportPreview;
import com.deveopsj.spending.dto.CardSpendingImportRow;

class SpendingControllerTest {

    @Test
    void 월_지출_합계에_미확정_고정지출을_포함한_예상_총지출을_제공한다() {
        SpendingService spendingService = mock(SpendingService.class);
        RecurringExpenseService recurringExpenseService = mock(RecurringExpenseService.class);
        SpendingController controller = new SpendingController(
                mock(DataInputService.class), spendingService, recurringExpenseService,
                mock(SpendingExcelImportService.class), mock(CardSpendingImportService.class));
        Member member = new Member();
        member.setMemberId(7L);
        YearMonth month = YearMonth.of(2026, 8);
        List<DailySpending> spendings = List.of(
                DailySpending.builder().amount(120_000L).build(),
                DailySpending.builder().amount(30_000L).build());
        RecurringExpense confirmedRule = recurringExpense(500_000L);
        RecurringExpense scheduledRule = recurringExpense(70_000L);

        when(spendingService.getSpendings(member, month.atDay(1), month.atEndOfMonth()))
                .thenReturn(spendings);
        when(recurringExpenseService.getOccurrences(member, month)).thenReturn(List.of(
                new RecurringExpenseOccurrence(confirmedRule, LocalDate.of(2026, 8, 25), true),
                new RecurringExpenseOccurrence(scheduledRule, LocalDate.of(2026, 8, 28), false)));
        ConcurrentModel model = new ConcurrentModel();

        assertThat(controller.spendingList("2026-08", model, member)).isEqualTo("spending/list");
        assertThat(model.getAttribute("totalAmount")).isEqualTo(150_000L);
        assertThat(model.getAttribute("scheduledRecurringAmount")).isEqualTo(70_000L);
        assertThat(model.getAttribute("expectedTotalAmount")).isEqualTo(220_000L);
    }

    @Test
    void 카드등록_검증오류시_미리보기와_선택값을_유지한다() {
        CardSpendingImportService cardImportService = mock(CardSpendingImportService.class);
        SpendingController controller = new SpendingController(
                mock(DataInputService.class), mock(SpendingService.class),
                mock(RecurringExpenseService.class), mock(SpendingExcelImportService.class),
                cardImportService);
        CardImportPreview preview = new CardImportPreview("token", CardCompany.SHINHAN,
                "sample.xlsx", List.of(new CardSpendingImportRow(2, LocalDate.of(2026, 8, 29),
                        "가맹점", 10_000L, "key", null)), null, 7L);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("cardSpendingImportPreview", preview);
        Member member = new Member(); member.setMemberId(7L);
        when(cardImportService.confirm(preview, List.of(0), java.util.Map.of(0, "FAKE"), member))
                .thenThrow(new IllegalArgumentException("카테고리가 올바르지 않습니다."));
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.confirmCardImport("token", List.of(0),
                java.util.Map.of("category_0", "FAKE"), member, session, model,
                new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("spending/import-preview");
        assertThat(model.getAttribute("preview")).isSameAs(preview);
        assertThat(model.getAttribute("selectedRows")).isEqualTo(List.of(0));
        assertThat(model.getAttribute("errorMessage")).isEqualTo("카테고리가 올바르지 않습니다.");
    }

    @Test
    void 삼성_청구월을_서비스에_전달하고_미리보기를_세션에_저장한다() {
        CardSpendingImportService service = mock(CardSpendingImportService.class);
        SpendingController controller = new SpendingController(
                mock(DataInputService.class), mock(SpendingService.class),
                mock(RecurringExpenseService.class), mock(SpendingExcelImportService.class), service);
        var file = new org.springframework.mock.web.MockMultipartFile("file", "samsung.xlsx", null, new byte[] {1});
        Member member = new Member(); member.setMemberId(7L);
        var preview = new CardImportPreview("token", CardCompany.SAMSUNG, "samsung.xlsx", List.of(), "2026-08", 7L);
        when(service.preview(CardCompany.SAMSUNG, file, null, YearMonth.of(2026, 8), member)).thenReturn(preview);
        var session = new MockHttpSession();
        var model = new ConcurrentModel();

        String view = controller.previewCardImport(CardCompany.SAMSUNG, file, null, "2026-08",
                member, model, session, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("spending/import-preview");
        assertThat(model.getAttribute("preview")).isSameAs(preview);
        assertThat(session.getAttribute("cardSpendingImportPreview")).isSameAs(preview);
    }

    @Test
    void 잘못된_청구월은_파일을_읽기전에_거부한다() {
        CardSpendingImportService service = mock(CardSpendingImportService.class);
        SpendingController controller = new SpendingController(
                mock(DataInputService.class), mock(SpendingService.class),
                mock(RecurringExpenseService.class), mock(SpendingExcelImportService.class), service);
        var file = new org.springframework.mock.web.MockMultipartFile("file", "samsung.xlsx", null, new byte[] {1});
        var redirect = new RedirectAttributesModelMap();

        String view = controller.previewCardImport(CardCompany.SAMSUNG, file, null, "invalid",
                new Member(), new ConcurrentModel(), new MockHttpSession(), redirect);

        assertThat(view).isEqualTo("redirect:/spending/list");
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("청구월은 YYYY-MM 형식으로 입력해 주세요.");
        org.mockito.Mockito.verifyNoInteractions(service);
    }

@Test
    void 일반엑셀도_저장전_중복미리보기를_제공한다() {
        var service = mock(SpendingExcelImportService.class);
        var controller = new SpendingController(mock(DataInputService.class), mock(SpendingService.class),
                mock(RecurringExpenseService.class), service, mock(CardSpendingImportService.class));
        Member member = new Member(); member.setMemberId(7L);
        var file = new org.springframework.mock.web.MockMultipartFile("file", "spending.xlsx", null, new byte[] {1});
        var preview = new com.deveopsj.spending.dto.SpendingExcelImportPreview("token", 7L, "spending.xlsx", "2026-08", List.of());
        when(service.preview(file, member, "2026-08")).thenReturn(preview);
        var session = new MockHttpSession();
        var model = new ConcurrentModel();
        assertThat(controller.importExcel(file, "2026-08", member, session, model, new RedirectAttributesModelMap()))
                .isEqualTo("spending/excel-import-preview");
        assertThat(session.getAttribute("spendingExcelImportPreview")).isSameAs(preview);
        org.mockito.Mockito.verify(service, org.mockito.Mockito.never()).importFile(file, member);
    }

    @Test
    void 확정한_엑셀토큰은_다시_사용할수없다() {
        var service = mock(SpendingExcelImportService.class);
        var controller = new SpendingController(mock(DataInputService.class), mock(SpendingService.class),
                mock(RecurringExpenseService.class), service, mock(CardSpendingImportService.class));
        Member member = new Member(); member.setMemberId(7L);
        var preview = new com.deveopsj.spending.dto.SpendingExcelImportPreview("token", 7L, "spending.xlsx", "2026-08", List.of());
        var session = new MockHttpSession();
        session.setAttribute("spendingExcelImportPreview", preview);
        when(service.confirm(preview, List.of(0), member)).thenReturn(1);
        var redirect = new RedirectAttributesModelMap();
        assertThat(controller.confirmExcelImport("token", List.of(0), member, session, new ConcurrentModel(), redirect))
                .isEqualTo("redirect:/spending/list");
        assertThat(session.getAttribute("spendingExcelImportPreview")).isNull();
        assertThat(redirect.get("month")).isEqualTo("2026-08");
        controller.confirmExcelImport("token", List.of(0), member, session, new ConcurrentModel(), new RedirectAttributesModelMap());
        org.mockito.Mockito.verify(service, org.mockito.Mockito.times(1)).confirm(preview, List.of(0), member);
    }

    @Test
    void 타인세션의_엑셀미리보기는_확정할수없다() {
        var service = mock(SpendingExcelImportService.class);
        var controller = new SpendingController(mock(DataInputService.class), mock(SpendingService.class),
                mock(RecurringExpenseService.class), service, mock(CardSpendingImportService.class));
        Member member = new Member(); member.setMemberId(8L);
        var session = new MockHttpSession();
        session.setAttribute("spendingExcelImportPreview",
                new com.deveopsj.spending.dto.SpendingExcelImportPreview("token", 7L, "spending.xlsx", null, List.of()));
        assertThat(controller.confirmExcelImport("token", List.of(0), member, session,
                new ConcurrentModel(), new RedirectAttributesModelMap())).isEqualTo("redirect:/spending/list");
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    private RecurringExpense recurringExpense(long amount) {
        return RecurringExpense.builder().amount(amount).build();
    }
}
