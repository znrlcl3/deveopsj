package com.deveopsj.assetplan.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.*;

@Entity @Getter @Setter
@Table(name="account_balance_snapshot",uniqueConstraints=@UniqueConstraint(name="uk_balance_file",columnNames={"plan_id","balance_date","digest"}))
public class AccountBalanceSnapshot {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="plan_id",nullable=false) private AssetPlan plan;
    @Column(nullable=false) private LocalDate balanceDate;
    @Column(nullable=false) private LocalDateTime importedAt;
    @Column(nullable=false,length=64) private String digest;
    @Column(nullable=false,length=200) private String fileName;
    @Column(nullable=false) private int formatVersion=1;
    @Lob @Column(nullable=false,columnDefinition="LONGTEXT") private String payload;
    @Lob @Column(nullable=false,columnDefinition="LONGBLOB") private byte[] originalFile;
}
