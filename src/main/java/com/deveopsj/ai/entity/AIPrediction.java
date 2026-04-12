package com.deveopsj.ai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

import com.deveopsj.common.entity.BaseEntity;
import com.deveopsj.member.entity.Member;

@Entity
@Table(name = "ai_predictions")
@Getter @Setter
public class AIPrediction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(nullable = false, length = 20)
    private String predictionType; // ASSET, SPENDING

    @Column(nullable = false)
    private LocalDate targetDate;

    @Column(nullable = false)
    private Long predictedAmount;

    @Column(columnDefinition = "TEXT")
    private String insightText;

    private Float confidenceScore;
}