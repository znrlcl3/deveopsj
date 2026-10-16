package com.deveopsj.spending.service;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.deveopsj.spending.dto.SpendingDuplicateMatch;
import com.deveopsj.spending.entity.DailySpending;

final class SpendingDuplicateSupport {
    private SpendingDuplicateSupport() {}
    record Key(LocalDate date, long amount, String memo) {}
    static Key key(LocalDate date, long amount, String memo) {
        return new Key(date, amount, memo == null ? "" : memo.replaceAll("\\s+", " ").trim());
    }
    static Map<Key, List<DailySpending>> index(List<DailySpending> existing) {
        return existing.stream().collect(Collectors.groupingBy(
                row -> key(row.getSpendingDate(), row.getAmount(), row.getMemo())));
    }
    static List<SpendingDuplicateMatch> matches(List<DailySpending> existing) {
        return existing.stream().map(row -> new SpendingDuplicateMatch(
                row.getId(), row.getCategoryCode(), row.getCreateDate())).toList();
    }
}
