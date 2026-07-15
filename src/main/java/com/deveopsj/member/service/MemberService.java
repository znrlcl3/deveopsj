package com.deveopsj.member.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deveopsj.member.dto.MemberJoinDto;
import com.deveopsj.member.entity.Member;
import com.deveopsj.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void join(MemberJoinDto joinDto) {
        if (memberRepository.findByLoginId(joinDto.getLoginId()).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 아이디입니다.");
        }

        Member member = new Member();
        member.setLoginId(joinDto.getLoginId());
        member.setPassword(passwordEncoder.encode(joinDto.getPassword()));
        member.setName(joinDto.getName());
        member.setRole("USER");

        memberRepository.save(member);
    }
}
