package com.stocklog.modules.member.application.usecase;

import com.stocklog.modules.member.entity.Member;

public interface GetMemberUseCase {
    Member getMemberReference(Long id);
}
