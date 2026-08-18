package com.deveopsj.member.repository;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

import com.deveopsj.member.entity.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByLoginId(String loginId);
    Optional<Member> findByOidcIssuerAndOidcSubject(String oidcIssuer, String oidcSubject);
    List<Member> findAllByOrderByCreateDateDesc();
}

