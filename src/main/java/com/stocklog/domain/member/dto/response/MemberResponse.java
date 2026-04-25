package com.stocklog.domain.member.dto.response;

import com.stocklog.domain.member.entity.Member;
import com.stocklog.domain.member.entity.type.MemberRole;
import lombok.*;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public class MemberResponse {
    private Long id;
    private String loginId;
    private String username;
    private MemberRole role;

    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getLoginId(),
                member.getUsername(),
                member.getRole()
        );
    }
}
