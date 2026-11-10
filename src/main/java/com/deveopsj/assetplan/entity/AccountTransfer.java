package com.deveopsj.assetplan.entity;

import com.deveopsj.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Entity @Table(name="account_transfer", indexes={@Index(name="idx_transfer_plan_date",columnList="plan_id,transfer_date"), @Index(name="idx_account_transfer_import_group",columnList="import_group_key")})
@Getter @Setter
public class AccountTransfer extends BaseEntity {
    @Column(length=64)
    private String importGroupKey;

    public enum Type { DEPOSIT, WITHDRAWAL }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="plan_id",nullable=false) private AssetPlan assetPlan;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Type type;
    @Column(nullable=false) private LocalDate transferDate;
    @Column(nullable=false) private LocalDate bookingDate;
    @Column(nullable=false) private Long amount;
    @Column(nullable=false) private Long grossAmount;
    @Column(nullable=false) private Long fee;
    @Column(nullable=false) private Long tax;
    @Column(nullable=false,length=100) private String description;
    @Column(nullable=false,length=100) private String sourceName;
    @Column(nullable=false,unique=true,length=64) private String importKey;
}
