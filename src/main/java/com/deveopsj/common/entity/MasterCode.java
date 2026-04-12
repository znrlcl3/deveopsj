package com.deveopsj.common.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "master_codes")
@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MasterCode {

    @Id
    @Column(name = "code_id", length = 20)
    private String codeId;

    @Column(name = "group_id", length = 20)
    private String groupId;

    @Column(name = "code_name", length = 100)
    private String codeName;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "create_date", updatable = false)
    private LocalDateTime createDate;

    @Column(name = "create_by", length = 50, updatable = false)
    private String createBy;

    @Column(name = "update_date")
    private LocalDateTime updateDate;

    @Column(name = "update_by", length = 50)
    private String updateBy;

    @Column(name = "disable_date")
    private LocalDateTime disableDate;
}