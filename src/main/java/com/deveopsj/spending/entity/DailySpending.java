package com.deveopsj.spending.entity;

import java.time.LocalDate;

import com.deveopsj.common.entity.BaseEntity;
import com.deveopsj.member.entity.Member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.*;

@Entity
@Table(name = "daily_spending", uniqueConstraints = @UniqueConstraint(
        name = "uk_daily_spending_recurring_month",
        columnNames = {"recurring_expense_id", "recurring_year_month"}))
@Getter @Setter
@Builder
@NoArgsConstructor 
@AllArgsConstructor
public class DailySpending extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(nullable = false)
    private LocalDate spendingDate;

    @Column(nullable = false, length = 20)
    private String categoryCode; // FOOD, TRANS 등

    @Column(nullable = false)
    private Long amount;

    @Column(length = 200)
    private String memo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recurring_expense_id")
    private RecurringExpense recurringExpense;

    @Column(name = "recurring_year_month", length = 7)
    private String recurringYearMonth;
}
