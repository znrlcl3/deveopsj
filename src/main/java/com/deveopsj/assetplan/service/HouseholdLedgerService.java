package com.deveopsj.assetplan.service;

import com.deveopsj.assetplan.dto.MonthlyDepositRequest;
import com.deveopsj.assetplan.entity.AssetPlan;
import com.deveopsj.assetplan.entity.AssetSavings;
import com.deveopsj.assetplan.repository.AssetPlanRepository;
import com.deveopsj.assetplan.repository.AssetSavingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class HouseholdLedgerService {
    private static final long MAX_AMOUNT = 999999999999999L;
    private final AssetPlanRepository plans;
    private final AssetSavingsRepository savings;

    public Summary summary(Long memberId, YearMonth month) {
        LocalDate today = LocalDate.now();
        var ownedPlans = plans.findByMemberMemberId(memberId);
        var entries = savings.findAllByMemberIdAndDepositDateLessThanEqual(memberId, today);
        var rows = new ArrayList<Row>();
        long total = 0, opening = 0, deposits = 0, withdrawals = 0;
        for (var plan : ownedPlans) {
            var history = entries.stream().filter(s -> s.getAssetPlan() != null
                    && Objects.equals(s.getAssetPlan().getId(), plan.getId())).toList();
            long initial = plan.getOpeningPrincipal() != null && plan.getOpeningDate() != null
                    && !plan.getOpeningDate().isAfter(today) ? plan.getOpeningPrincipal() : 0;
            long principal = initial;
            long monthlyIn = 0, monthlyOut = 0;
            for (var s : history) {
                principal = Math.addExact(principal, s.getAmount());
                if (YearMonth.from(s.getDepositDate()).equals(month)) {
                    if (s.getAmount() > 0) monthlyIn = Math.addExact(monthlyIn, s.getAmount());
                    else monthlyOut = Math.subtractExact(monthlyOut, s.getAmount());
                }
            }
            opening = Math.addExact(opening, initial);
            total = Math.addExact(total, principal);
            deposits = Math.addExact(deposits, monthlyIn);
            withdrawals = Math.addExact(withdrawals, monthlyOut);
            long overlapCount = history.stream().filter(s -> plan.getOpeningDate() != null
                    && !s.getDepositDate().isAfter(plan.getOpeningDate())).count();
            rows.add(new Row(plan, initial, principal, monthlyIn, monthlyOut, overlapCount));
        }
        long extra = 0;
        for (var s : entries) {
            if (s.getAssetPlan() != null || s.getGoal() == null || s.getGoal().getMember() == null
                    || !Objects.equals(s.getGoal().getMember().getMemberId(), memberId)) continue;
            extra = Math.addExact(extra, s.getAmount());
            if (YearMonth.from(s.getDepositDate()).equals(month)) {
                if (s.getAmount() > 0) deposits = Math.addExact(deposits, s.getAmount());
                else withdrawals = Math.subtractExact(withdrawals, s.getAmount());
            }
        }
        return new Summary(List.copyOf(rows), Math.addExact(total, extra), opening, extra, deposits, withdrawals);
    }

    @Transactional
    public void opening(Long memberId, Long planId, Long amount, LocalDate date,
                        Long expectedAmount, LocalDate expectedDate) {
        validDate(date);
        if (amount == null || amount < 0 || amount > MAX_AMOUNT) throw new IllegalArgumentException("시작 원금은 0 이상의 원화 금액으로 입력하세요.");
        var plan = owned(memberId, planId);
        if (!Objects.equals(plan.getOpeningPrincipal(), expectedAmount) || !Objects.equals(plan.getOpeningDate(), expectedDate))
            throw new IllegalArgumentException("시작 원금이 변경되었습니다. 새로고침 후 다시 확인하세요.");
        boolean earlierRecord = savings.findAllByMemberId(memberId).stream().anyMatch(s ->
                s.getAssetPlan() != null && Objects.equals(s.getAssetPlan().getId(), planId) && !s.getDepositDate().isAfter(date));
        if (earlierRecord) throw new IllegalArgumentException("시작 기준일은 기존 첫 납입일보다 빨라야 합니다. 기준일 이전·당일 내역과 중복되지 않도록 확인하세요.");
        plan.setOpeningPrincipal(amount);
        plan.setOpeningDate(date);
    }

    @Transactional
    public int confirmMonth(Long memberId, MonthlyDepositRequest request) {
        validDate(request.getDate());
        if (request.getRows() == null || request.getRows().size() > 100) throw new IllegalArgumentException("한 번에 100개 이하의 플랜을 선택하세요.");
        var selected = request.getRows().stream().filter(Objects::nonNull).filter(MonthlyDepositRequest.Row::isSelected).toList();
        if (selected.isEmpty()) throw new IllegalArgumentException("실제 납입한 플랜을 선택하세요.");
        Set<Long> ids = new HashSet<>();
        for (var row : selected) {
            if (row.getPlanId() == null || !ids.add(row.getPlanId()) || row.getAmount() == null
                    || row.getAmount() <= 0 || row.getAmount() > MAX_AMOUNT) throw new IllegalArgumentException("플랜과 0보다 큰 실제 납입액을 확인하세요.");
        }
        // Lock in deterministic order; concurrent submissions re-check existing deposits after the lock.
        var ownedPlans = new HashMap<Long, AssetPlan>();
        ids.stream().sorted().forEach(id -> ownedPlans.put(id, owned(memberId, id)));
        var month = YearMonth.from(request.getDate());
        var existing = savings.findAllByMemberIdAndDepositDateBetween(memberId, month.atDay(1), month.atEndOfMonth());
        var inserts = new ArrayList<AssetSavings>();
        for (var row : selected) {
            var plan = ownedPlans.get(row.getPlanId());
            if (plan.openingCoversMonth(month))
                throw new IllegalArgumentException("시작 원금 기준월까지는 월별 일괄 납입에서 제외합니다. 기준일 이후 추가 거래는 추가 납입·인출에서 기록하세요.");
            plan.requireAfterOpening(request.getDate());
            if (existing.stream().anyMatch(s -> s.getAssetPlan() != null && Objects.equals(s.getAssetPlan().getId(), plan.getId()) && s.getAmount() > 0))
                throw new IllegalArgumentException("이미 이 달의 납입이 있는 플랜입니다. 내역에서 확인·수정하거나 추가 납입을 별도로 기록하세요.");
            inserts.add(AssetSavings.builder().assetPlan(plan).goal(plan.getGoal()).depositType(AssetSavings.DepositType.PLAN)
                    .amount(row.getAmount()).depositDate(request.getDate()).memo("월별 납입 확인").build());
        }
        savings.saveAll(inserts);
        return inserts.size();
    }

    private AssetPlan owned(Long memberId, Long planId) {
        if (planId == null) throw new IllegalArgumentException("플랜을 선택하세요.");
        return plans.lockOwned(planId, memberId).orElseThrow(() -> new IllegalArgumentException("사용할 수 있는 플랜이 없습니다."));
    }
    private void validDate(LocalDate date) {
        if (date == null || date.getYear() < 1900 || date.isAfter(LocalDate.now())) throw new IllegalArgumentException("1900년부터 오늘까지의 날짜를 입력하세요.");
    }
    public record Row(AssetPlan plan, long opening, long principal, long monthlyIn, long monthlyOut, long overlapCount) {
        public String name() { return plan.getPlanName() == null || plan.getPlanName().isBlank() ? "이름 없는 플랜" : plan.getPlanName(); }
    }
    public record Summary(List<Row> rows, long principal, long opening, long extra, long deposits, long withdrawals) {
        public long netDeposit() { return Math.subtractExact(deposits, withdrawals); }
    }
}
