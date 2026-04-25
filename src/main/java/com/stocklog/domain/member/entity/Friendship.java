package com.stocklog.domain.member.entity;

import com.stocklog.domain.member.entity.type.FriendStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Friendship {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_member_id")
    private Member fromMember;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_member_id")
    private Member toMember;

    @Enumerated(EnumType.STRING)
    private FriendStatus status;

    private LocalDateTime createdAt;

    @Builder
    public Friendship(Member fromMember, Member toMember, FriendStatus status) {
        this.fromMember = fromMember;
        this.toMember = toMember;
        this.status = status;
    }

    public void accept() {
        this.status = FriendStatus.ACCEPTED;
    }

    public Member opponent(Member memberA) {
        if (memberA.equals(fromMember)) return toMember;
        return fromMember;
    }

}


