package com.deveopsj.assetplan.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

import com.deveopsj.common.entity.BaseEntity;

@Entity
@Table(name = "asset_savings")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetSavings extends BaseEntity {

    public enum DepositType {
        PLAN, EXTRA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private AssetPlan assetPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id")
    private Goal goal;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private DepositType depositType;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private LocalDate depositDate;

    @Column(length = 200)
    private String memo;
}
