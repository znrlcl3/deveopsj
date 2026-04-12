package com.deveopsj.assetplan.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

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

    @Column(nullable = false)
    private LocalDate planDate;

    @Column(nullable = false, length = 20)
    private String assetType; // INVEST_ISA, INVEST_IRP 등

    @Column(nullable = false)
    private Long amount;

    @Column(length = 200)
    private String memo;
}