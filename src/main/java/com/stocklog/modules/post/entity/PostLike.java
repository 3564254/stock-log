package com.stocklog.modules.post.entity;

import com.stocklog.global.common.base.BaseTimeEntity;
import com.stocklog.modules.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 게시글 좋아요 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "post_like", uniqueConstraints = {
        @UniqueConstraint(name = "uk_post_member", columnNames = {"post_id", "member_id"})
})
public class PostLike extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    public PostLike(Post post, Member member) {
        this.post = post;
        this.member = member;
    }
}
