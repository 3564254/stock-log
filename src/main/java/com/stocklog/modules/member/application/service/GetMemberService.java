package com.stocklog.modules.member.application.service;

import com.stocklog.modules.member.application.usecase.GetMemberUseCase;
import com.stocklog.modules.member.entity.Member;
import com.stocklog.modules.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetMemberService implements GetMemberUseCase {
    private final MemberService memberService;

    @Override
    public Member getMemberReference(Long id) {
        return memberService.getReferenceById(id);
    }
}
