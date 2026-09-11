package com.stocklog.modules.member.dto.response;

import com.stocklog.modules.member.entity.Member;
import com.stocklog.modules.member.entity.type.MemberRole;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public class MemberDetailResponse {
    private Long memberId;
    private String username;
    private String loginId;
    private String password;
    private MemberRole role;

    public static MemberDetailResponse from(Member member) {
        return new MemberDetailResponse(
                member.getId(),
                member.getUsername(),
                member.getLoginId(),
                member.getPassword(),
                member.getRole()
        );
    }
}
