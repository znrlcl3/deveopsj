package com.deveopsj.member.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.deveopsj.member.entity.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {}

