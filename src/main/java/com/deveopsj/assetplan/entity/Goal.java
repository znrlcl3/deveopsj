package com.deveopsj.assetplan.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

import com.deveopsj.common.entity.BaseEntity;
import com.deveopsj.member.entity.Member;

@Entity
@Table(name = "goals")
@Getter @Setter
public class Goal extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(nullable = false)
    private String title; // 목표명 (예: 1억 모으기, 유럽 여행)

    @Column(nullable = false)
    private Long targetAmount; // 목표 금액

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoalType type; // PERPETUAL(무제한), TERM(기간제한)

    private LocalDate startDate; // 기간제한일 때 시작일
    private LocalDate endDate;   // 기간제한일 때 종료일

    @Column(nullable = false, length = 20)
    private String status; // IN_PROGRESS, COMPLETED, FAILED

    public enum GoalType {
        PERPETUAL, TERM
    }
}
