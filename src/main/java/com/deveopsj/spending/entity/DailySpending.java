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
import lombok.*;

@Entity
@Table(name = "daily_spending")
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
}