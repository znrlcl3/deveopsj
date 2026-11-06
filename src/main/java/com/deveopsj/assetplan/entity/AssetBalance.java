package com.deveopsj.assetplan.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

/** Latest manually confirmed balance, not an income or contribution transaction. */
@Entity @Table(name = "asset_balance", indexes = @Index(name = "idx_asset_balance_member", columnList = "memberId")) @Getter @Setter
public class AssetBalance {
    public enum Kind {
        DEPOSIT("예금"), SAVINGS("적금"), CASH("현금·입출금통장");
        private final String label;
        Kind(String label) { this.label = label; }
        public String getLabel() { return label; }
    }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long memberId;
    @Column(nullable = false, length = 80) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Kind kind;
    @Column(nullable = false) private Long amount;
    @Column(nullable = false) private LocalDate asOfDate;
    private LocalDate maturityDate;
    @Version private Long version;
}
