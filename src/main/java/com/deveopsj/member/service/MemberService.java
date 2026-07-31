package com.deveopsj.member.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deveopsj.member.dto.MemberJoinDto;
import com.deveopsj.member.dto.PasswordChangeDto;
import com.deveopsj.member.dto.AccountDeactivationDto;
import com.deveopsj.member.dto.AdminMemberView;
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

    @Transactional
    public void changePassword(String loginId, PasswordChangeDto changeDto) {
        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));
        if (!passwordEncoder.matches(changeDto.getCurrentPassword(), member.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
        }
        if (!changeDto.getNewPassword().equals(changeDto.getConfirmPassword())) {
            throw new IllegalArgumentException("새 비밀번호와 확인 값이 일치하지 않습니다.");
        }
        if (passwordEncoder.matches(changeDto.getNewPassword(), member.getPassword())) {
            throw new IllegalArgumentException("새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }

        member.setPassword(passwordEncoder.encode(changeDto.getNewPassword()));
        memberRepository.save(member);
    }

    @Transactional
    public void deactivateAccount(String loginId, AccountDeactivationDto deactivationDto) {
        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));
        if (member.getDisableDate() != null) {
            throw new IllegalArgumentException("이미 비활성화된 계정입니다.");
        }
        if (!passwordEncoder.matches(deactivationDto.getCurrentPassword(), member.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
        }
        if (!"회원탈퇴".equals(deactivationDto.getConfirmation())) {
            throw new IllegalArgumentException("확인 문구로 회원탈퇴를 정확히 입력해 주세요.");
        }

        member.setDisableDate(LocalDateTime.now());
        memberRepository.save(member);
    }

    public List<AdminMemberView> getMembersForAdmin() {
        return memberRepository.findAllByOrderByCreateDateDesc().stream()
                .map(AdminMemberView::from)
                .toList();
    }

    @Transactional
    public void changeMemberActiveStatus(
            String adminLoginId, Long memberId, boolean active) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
        if (!active && member.getLoginId().equals(adminLoginId)) {
            throw new IllegalArgumentException("현재 로그인한 관리자 계정은 비활성화할 수 없습니다.");
        }

        member.setDisableDate(active ? null : LocalDateTime.now());
        memberRepository.save(member);
    }

}
