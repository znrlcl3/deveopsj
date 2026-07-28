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
}
