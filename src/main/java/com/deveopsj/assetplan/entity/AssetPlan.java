package com.deveopsj.assetplan.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import com.deveopsj.common.entity.BaseEntity;
import com.deveopsj.member.entity.Member;

@Entity
@Table(name = "asset_plan")
@Getter @Setter
public class AssetPlan extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id")
    private Goal goal;

    @Column(nullable = false, length = 20)
    private String assetType;

    @Column(length = 100)
    private String planName;

    @Column(nullable = false)
    private Long monthlyAmount;

    @Column(length = 200)
    private String memo;

    // Existing principal is separate from monthly cash movements.
    private Long openingPrincipal;
    private java.time.LocalDate openingDate;

    public boolean openingCoversMonth(java.time.YearMonth month) {
        return openingDate != null && !month.isAfter(java.time.YearMonth.from(openingDate));
    }

    public void requireAfterOpening(java.time.LocalDate date) {
        if (openingDate != null && (date == null || !date.isAfter(openingDate))) {
            throw new IllegalArgumentException("시작 원금에는 기준일까지의 납입·인출이 포함됩니다. 기준일 다음 날부터 기록하세요.");
        }
    }

}
