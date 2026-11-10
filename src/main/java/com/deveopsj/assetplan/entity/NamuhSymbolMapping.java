package com.deveopsj.assetplan.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Getter @Setter
@Table(name="namuh_symbol_mapping",uniqueConstraints=@UniqueConstraint(name="uk_namuh_member_name",columnNames={"member_id","source_name"}))
public class NamuhSymbolMapping {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="member_id",nullable=false) private com.deveopsj.member.entity.Member member;
    @Column(name="source_name",nullable=false,length=100) private String sourceName;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="asset_id",nullable=false) private InvestmentAsset asset;
}
