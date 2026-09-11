package com.stocklog.modules.member.service;

import com.stocklog.modules.member.dto.request.LoginRequest;
import com.stocklog.modules.member.dto.request.PasswordRequest;
import com.stocklog.modules.member.dto.request.SignupRequest;
import com.stocklog.modules.member.dto.response.MemberResponse;
import com.stocklog.modules.member.entity.Member;
import com.stocklog.global.error.BusinessException;
import com.stocklog.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Member signUp(SignupRequest request) {

        String encodedPassword = passwordEncoder.encode(request.getPassword());
        return Member.builder()
                .loginId(request.getLoginId())
                .password(encodedPassword)
                .username(request.getUsername())
                .build();
    }

    public MemberResponse login(LoginRequest request, Optional<Member> member) {

        return member
                .filter(m -> passwordEncoder.matches(request.getPassword(), m.getPassword()))
                .map(MemberResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILURE));
    }

    @Transactional
    public void updatePassword(Member member, PasswordRequest request) {
        validateCurrentPassword(request.getCurrentPassword(), member.getPassword());
        member.changePassword(passwordEncoder.encode(request.getNewPassword()));
    }

    private void validateCurrentPassword(String raw, String encoded) {
        if (!passwordEncoder.matches(raw, encoded)) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }
    }
}