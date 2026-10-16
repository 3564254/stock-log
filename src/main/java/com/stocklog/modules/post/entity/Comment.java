package com.stocklog.modules.post.entity;

import com.stocklog.global.common.base.BaseTimeEntity;
import com.stocklog.modules.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * 댓글. parent 가 null 이면 댓글, 있으면 대댓글(1단계만 허용 — CommentService 에서 검증).
 * 대댓글 목록은 children 컬렉션 대신 parent_id IN (...) 한 번으로 가져온다.
 *
 * 인덱스 idx_comment_post_parent_id(post_id, parent_id, comment_id)
 *   → "이 글의 댓글(parent IS NULL)을 id 순으로 N개" 를 정렬 없이 인덱스 범위로 읽는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "comment", indexes = {
        @Index(name = "idx_comment_post_parent_id", columnList = "post_id, parent_id, comment_id")
})
public class Comment extends BaseTimeEntity {

    public static final int CONTENT_MAX = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    @Column(nullable = false, length = CONTENT_MAX)
    private String content;

    @Column(nullable = false)
    private long likeCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status;   // ACTIVE / DELETED

    public Comment(Post post, Member member, Comment parent, String content) {
        this.post = post;
        this.member = member;
        this.parent = parent;
        this.content = content;
        this.status = PostStatus.ACTIVE;
    }

    public void delete() {
        this.status = PostStatus.DELETED;
    }

    public boolean isActive() {
        return status == PostStatus.ACTIVE;
    }

    public boolean isReply() {
        return parent != null;
    }

    public boolean isWrittenBy(Long memberId) {
        return memberId != null && Objects.equals(member.getId(), memberId);
    }
}
