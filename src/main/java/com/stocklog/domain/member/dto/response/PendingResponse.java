package com.stocklog.domain.member.dto.response;

import com.stocklog.domain.member.entity.Friendship;
import com.stocklog.domain.member.entity.type.FriendStatus;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
public class PendingResponse {
    private Long friendshipId;
    private String requesterName;
    private Long fromId;
    private Long toId;
    private final FriendStatus friendStatus = FriendStatus.PENDING;

    public static PendingResponse from(Friendship friendship) {
        return PendingResponse.builder()
                .friendshipId(friendship.getId())
                .fromId(friendship.getFromMember().getId())
                .toId(friendship.getToMember().getId())
                .requesterName(friendship.getFromMember().getUsername())
                .build();
    }

}
