package com.stocklog.modules.member.service;

import com.stocklog.modules.member.dto.response.MemberDetailResponse;
import com.stocklog.modules.member.dto.response.MemberResponse;
import com.stocklog.modules.member.entity.Member;
import com.stocklog.modules.member.repository.MemberRepository;
import com.stocklog.global.error.BusinessException;
import com.stocklog.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class MemberService {
    private final MemberRepository memberRepository;

    public void save(Member member) {
        memberRepository.save(member);
    }

    public MemberResponse getInfo(Long memberId) {
        return memberRepository.findById(memberId)
                .map(MemberResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    public MemberDetailResponse getDetailInfo(Long memberId) {
        return memberRepository.findById(memberId)
                .map(MemberDetailResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

    }

    public MemberResponse getMemberResponseByLoginId(String loginId) {
        return memberRepository.findByLoginId(loginId)
                .map(MemberResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    public Optional<Member> findByLoginId(String loginId) {
        return memberRepository.findByLoginId(loginId);
    }

    public Member findById(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }


    public Member getReferenceById(Long id) {
        return memberRepository.getReferenceById(id);
    }

    @Transactional
    public void updateUsername(Long memberId, String newUsername) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        member.changeUsername(newUsername);
    }

    @Transactional
    public void deleteById(Long id) {
        memberRepository.deleteById(id);
    }

    public boolean isLoginIdDuplicate(String loginId) {
        return memberRepository.findByLoginId(loginId).isPresent();
    }
}