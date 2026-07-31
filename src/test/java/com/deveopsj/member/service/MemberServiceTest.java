package com.deveopsj.member.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.deveopsj.member.dto.PasswordChangeDto;
import com.deveopsj.member.dto.AccountDeactivationDto;
import com.deveopsj.member.entity.Member;
import com.deveopsj.member.repository.MemberRepository;

class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private MemberService memberService;
    private Member member;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        memberService = new MemberService(memberRepository, passwordEncoder);
        member = new Member();
        member.setLoginId("user");
        member.setPassword("encoded-current");
        when(memberRepository.findByLoginId("user")).thenReturn(Optional.of(member));
    }

    @Test
    void 현재비밀번호를_확인하고_새비밀번호를_암호화한다() {
        PasswordChangeDto dto = dto("current-password", "new-password", "new-password");
        when(passwordEncoder.matches("current-password", "encoded-current")).thenReturn(true);
        when(passwordEncoder.matches("new-password", "encoded-current")).thenReturn(false);
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-new");

        memberService.changePassword("user", dto);

        verify(memberRepository).findByLoginId("user");
        verify(memberRepository).save(member);
        verify(passwordEncoder).encode("new-password");
    }

    @Test
    void 현재비밀번호가_틀리면_저장하지_않는다() {
        PasswordChangeDto dto = dto("wrong-password", "new-password", "new-password");
        when(passwordEncoder.matches("wrong-password", "encoded-current")).thenReturn(false);

        assertThatThrownBy(() -> memberService.changePassword("user", dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("현재 비밀번호가 올바르지 않습니다.");

        verify(memberRepository, never()).save(any());
    }

    @Test
    void 새비밀번호_확인이_다르면_저장하지_않는다() {
        PasswordChangeDto dto = dto("current-password", "new-password", "different-password");
        when(passwordEncoder.matches("current-password", "encoded-current")).thenReturn(true);

        assertThatThrownBy(() -> memberService.changePassword("user", dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("새 비밀번호와 확인 값이 일치하지 않습니다.");

        verify(memberRepository, never()).save(any());
    }

    @Test
    void 현재비밀번호와_확인문구가_맞으면_계정을_비활성화한다() {
        AccountDeactivationDto dto = deactivationDto("current-password", "회원탈퇴");
        when(passwordEncoder.matches("current-password", "encoded-current")).thenReturn(true);

        memberService.deactivateAccount("user", dto);

        assertThat(member.getDisableDate()).isNotNull();
        verify(memberRepository).save(member);
    }

    @Test
    void 탈퇴확인문구가_틀리면_비활성화하지_않는다() {
        AccountDeactivationDto dto = deactivationDto("current-password", "탈퇴");
        when(passwordEncoder.matches("current-password", "encoded-current")).thenReturn(true);

        assertThatThrownBy(() -> memberService.deactivateAccount("user", dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("확인 문구로 회원탈퇴를 정확히 입력해 주세요.");

        assertThat(member.getDisableDate()).isNull();
        verify(memberRepository, never()).save(any());
    }

    @Test
    void 관리자가_비활성화된_회원을_활성화한다() {
        member.setMemberId(9L);
        member.setDisableDate(LocalDateTime.now());
        when(memberRepository.findById(9L)).thenReturn(Optional.of(member));

        memberService.changeMemberActiveStatus("admin", 9L, true);

        assertThat(member.getDisableDate()).isNull();
        verify(memberRepository).save(member);
    }

    @Test
    void 관리자는_자기계정을_비활성화할_수_없다() {
        member.setMemberId(9L);
        member.setLoginId("admin");
        when(memberRepository.findById(9L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() ->
                memberService.changeMemberActiveStatus("admin", 9L, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("현재 로그인한 관리자 계정은 비활성화할 수 없습니다.");

        verify(memberRepository, never()).save(any());
    }

    @Test
    void 관리자목록은_비밀번호를_노출하지_않는_DTO로_변환한다() {
        member.setMemberId(9L);
        member.setName("사용자");
        member.setRole("USER");
        when(memberRepository.findAllByOrderByCreateDateDesc())
                .thenReturn(List.of(member));

        var result = memberService.getMembersForAdmin();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).loginId()).isEqualTo("user");
        assertThat(result.get(0).active()).isTrue();
    }

    private PasswordChangeDto dto(String current, String password, String confirm) {
        PasswordChangeDto dto = new PasswordChangeDto();
        dto.setCurrentPassword(current);
        dto.setNewPassword(password);
        dto.setConfirmPassword(confirm);
        return dto;
    }

    private AccountDeactivationDto deactivationDto(String current, String confirmation) {
        AccountDeactivationDto dto = new AccountDeactivationDto();
        dto.setCurrentPassword(current);
        dto.setConfirmation(confirmation);
        return dto;
    }
}
