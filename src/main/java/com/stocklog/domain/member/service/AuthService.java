package com.stocklog.domain.member.service;

import com.stocklog.domain.member.dto.request.*;
import com.stocklog.domain.member.dto.response.MemberDetailResponse;
import com.stocklog.domain.member.dto.response.MemberResponse;
import com.stocklog.domain.member.entity.Member;
import com.stocklog.global.error.member.InvalidPasswordException;
import com.stocklog.global.error.member.LoginFailureException;
import com.stocklog.global.error.member.SignUpFailureException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {
    private final MemberService memberService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void signUp(SignupRequest request) {
        if (memberService.isLoginIdDuplicate(request.getLoginId())) {
            throw new SignUpFailureException("이미 사용 중인 아이디입니다.");
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());
        Member member = Member.builder()
                .loginId(request.getLoginId())
                .password(encodedPassword)
                .username(request.getUsername())
                .build();
        memberService.save(member);
    }

    public MemberResponse login(LoginRequest request) {

        Member member = memberService.findByLoginId(request.getLoginId())
                .orElseThrow(() -> new LoginFailureException("아이디 또는 비밀번호가 일치하지 않습니다."));
        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new LoginFailureException("아이디 또는 비밀번호가 일치하지 않습니다.");
        }
        return MemberResponse.from(member);
    }

    @Transactional
    public void updatePassword(Long memberId, PasswordRequest request) {
        Member member = memberService.findById(memberId);
        validateCurrentPassword(request.getCurrentPassword(), member.getPassword());
        member.changePassword(passwordEncoder.encode(request.getNewPassword()));
    }



    private void validateCurrentPassword(String raw, String encoded) {
        if (!passwordEncoder.matches(raw,encoded)) {
            throw new InvalidPasswordException("기존 비밀번호가 일치하지 않습니다.");
        }
    }




}
