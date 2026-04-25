package com.stocklog.domain.member.service;

import com.stocklog.domain.member.dto.request.UpdateRequest;
import com.stocklog.domain.member.dto.response.MemberDetailResponse;
import com.stocklog.domain.member.dto.response.MemberResponse;
import com.stocklog.domain.member.entity.Member;
import com.stocklog.domain.member.repository.MemberRepository;
import com.stocklog.global.error.member.LoginFailureException;
import jakarta.persistence.EntityNotFoundException;
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
        return MemberResponse.from(memberRepository.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("회원을 찾을 수 없습니다."))
        );
    }

    public MemberDetailResponse getDetailInfo(Long memberId) {
        return MemberDetailResponse.from(memberRepository.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("회원을 찾을 수 없습니다.")));
    }

    public Optional<Member> findByLoginId(String loginId) {
        return memberRepository.findByLoginId(loginId);
    }

    public MemberResponse getMemberResponseByLoginId(String loginId) {
        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new EntityNotFoundException("회원을 찾을 수 없습니다."));
        return MemberResponse.from(member);
    }

    public Member findById(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("회원을 찾을 수 없습니다."));
    }

    public Member getReference(Long id) {
        return memberRepository.getReferenceById(id);
    }

    @Transactional
    public void updateUsername(Long memberId, String newUsername) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("회원을 찾을 수 없습니다."));
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
