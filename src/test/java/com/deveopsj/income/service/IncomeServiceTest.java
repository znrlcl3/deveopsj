package com.deveopsj.income.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.deveopsj.income.dto.IncomeSaveRequest;
import com.deveopsj.income.dto.IncomeUpdateRequest;
import com.deveopsj.income.entity.Income;
import com.deveopsj.income.entity.Income.IncomeType;
import com.deveopsj.income.repository.IncomeRepository;
import com.deveopsj.member.entity.Member;

@ExtendWith(MockitoExtension.class)
class IncomeServiceTest {

    @Mock
    private IncomeRepository incomeRepository;

    @Test
    void 인증사용자소유로_수입을_저장한다() {
        IncomeService service = new IncomeService(incomeRepository);
        Member member = member(7L);
        IncomeSaveRequest request = saveRequest();
        request.setMemo("  7월 급여  ");

        service.save(request, member);

        ArgumentCaptor<Income> captor = ArgumentCaptor.forClass(Income.class);
        verify(incomeRepository).save(captor.capture());
        assertThat(captor.getValue().getMember()).isSameAs(member);
        assertThat(captor.getValue().getIncomeType()).isEqualTo(IncomeType.SALARY);
        assertThat(captor.getValue().getAmount()).isEqualTo(3_000_000L);
        assertThat(captor.getValue().getMemo()).isEqualTo("7월 급여");
    }

    @Test
    void 선택월의_시작일부터_마지막일까지_본인수입만_조회한다() {
        IncomeService service = new IncomeService(incomeRepository);
        Member member = member(7L);
        YearMonth month = YearMonth.of(2026, 7);
        when(incomeRepository
                .findByMemberMemberIdAndIncomeDateBetweenOrderByIncomeDateDescIdDesc(
                        7L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
                .thenReturn(List.of());

        service.getIncomesByMemberAndMonth(member, month);

        verify(incomeRepository)
                .findByMemberMemberIdAndIncomeDateBetweenOrderByIncomeDateDescIdDesc(
                        7L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
    }

    @Test
    void 다른사용자의_수입은_수정할수없다() {
        IncomeService service = new IncomeService(incomeRepository);
        Member member = member(7L);
        IncomeUpdateRequest request = new IncomeUpdateRequest();
        request.setId(11L);
        request.setIncomeDate(LocalDate.now());
        request.setIncomeType(IncomeType.OTHER);
        request.setAmount(100_000L);
        when(incomeRepository.findByIdAndMemberMemberId(11L, 7L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(request, member))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("수정할 수 있는 수입 내역이 없습니다.");
    }

    private IncomeSaveRequest saveRequest() {
        IncomeSaveRequest request = new IncomeSaveRequest();
        request.setIncomeDate(LocalDate.now());
        request.setIncomeType(IncomeType.SALARY);
        request.setAmount(3_000_000L);
        return request;
    }

    private Member member(Long id) {
        Member member = new Member();
        member.setMemberId(id);
        return member;
    }
}
