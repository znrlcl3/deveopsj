package com.deveopsj.assetplan.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
@Entity @Getter @Setter @Table(name="foreign_account_entry")
public class ForeignAccountEntry extends com.deveopsj.common.entity.BaseEntity {
    public enum Type { DIVIDEND, INTEREST, DEPOSIT, WITHDRAWAL }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="plan_id",nullable=false) private AssetPlan assetPlan;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Type type;
    @Column(nullable=false) private LocalDate entryDate;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal amount;
    @Column(nullable=false,length=3) private String currency="USD";
    @Column(nullable=false,length=64,unique=true) private String importKey;
    @Column(nullable=false,length=64) private String importGroupKey;
    @Column(length=2000) private String sourceDetails;
    public String getTypeLabel() { return switch(type) { case DIVIDEND -> "배당";case INTEREST -> "이자";case DEPOSIT -> "입금";case WITHDRAWAL -> "출금"; }; }
}
