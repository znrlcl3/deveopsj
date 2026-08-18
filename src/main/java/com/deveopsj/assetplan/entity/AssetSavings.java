package com.deveopsj.assetplan.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

import com.deveopsj.common.entity.BaseEntity;

@Entity
@Table(name = "asset_savings", uniqueConstraints = @UniqueConstraint(
        name = "uk_asset_savings_recurring_month",
        columnNames = {"recurring_savings_id", "recurring_year_month"}))
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recurring_savings_id")
    private RecurringSavings recurringSavings;

    @Column(name = "recurring_year_month", length = 7)
    private String recurringYearMonth;
}
