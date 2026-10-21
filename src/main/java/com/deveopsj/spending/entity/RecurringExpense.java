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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "recurring_expense", uniqueConstraints = @UniqueConstraint(
        name = "uk_recurring_card_installment", columnNames = {"member_id", "card_installment_key"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringExpense extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    private String categoryCode;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private Integer paymentDay;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    @Column(nullable = false)
    private boolean active;

    @Column(length = 200)
    private String memo;

    @Column(name = "card_installment_key", length = 64, updatable = false)
    private String cardInstallmentKey;
}
