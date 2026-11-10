package com.deveopsj.assetplan.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Getter @Setter
@Table(name="namuh_import_archive",uniqueConstraints=@UniqueConstraint(name="uk_archive_member_plan_digest",columnNames={"member_id","plan_id","digest"}))
public class NamuhImportArchive extends com.deveopsj.common.entity.BaseEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="member_id",nullable=false) private com.deveopsj.member.entity.Member member;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="plan_id",nullable=false) private AssetPlan assetPlan;
    @Column(nullable=false,length=200) private String fileName;
    @Column(nullable=false,length=64) private String digest;
    @Column(nullable=false) private long sizeBytes;
    @Lob @Column(nullable=false,columnDefinition="LONGBLOB") private byte[] content;
}
