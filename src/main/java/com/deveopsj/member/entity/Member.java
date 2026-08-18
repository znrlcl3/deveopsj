package com.deveopsj.member.entity;

import com.deveopsj.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "members", uniqueConstraints = @UniqueConstraint(
        name = "uk_members_oidc_identity", columnNames = {"oidc_issuer", "oidc_subject"}))
@Getter @Setter
public class Member extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long memberId;

    @Column(nullable = false, unique = true, length = 50)
    private String loginId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(length = 20)
    private String role; // ADMIN, USER

    @Column(name = "oidc_issuer", length = 255)
    private String oidcIssuer;

    @Column(name = "oidc_subject", length = 255)
    private String oidcSubject;
}
