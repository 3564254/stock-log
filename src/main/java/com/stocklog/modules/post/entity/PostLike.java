package com.stocklog.modules.post.entity;

import com.stocklog.global.common.base.BaseTimeEntity;
import com.stocklog.modules.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게시글 좋아요. 한 사람이 한 글에 한 번만 — uk_post_member 가 최종 방어선이다.
 * (서비스의 존재 확인만으로는 동시에 두 번 누르면 둘 다 "없음"으로 보고 두 줄이 들어갈 수 있다)
 * 유니크 인덱스 (post_id, member_id) 가 "이 글에 내가 눌렀나" 조회에도 그대로 쓰인다.
 */
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
