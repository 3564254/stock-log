package com.stocklog.modules.post.entity;

import com.stocklog.global.common.base.BaseTimeEntity;
import com.stocklog.modules.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * 커뮤니티 게시글.
 * - 작성자(Member)만 단방향으로 참조한다. 댓글·좋아요·태그는 각 Repository 에서 조회한다.
 * - like/comment/view 카운트는 목록에서 COUNT(*) 를 피하려고 둔 비정규화 컬럼이다.
 *   값은 엔티티 setter 가 아니라 PostRepository 의 UPDATE ... SET x = x + 1 로만 바꾼다(동시 요청에도 안 어긋나게).
 * - 삭제는 status = DELETED (soft delete). 댓글이 달린 글을 지워도 댓글 FK 가 깨지지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "post")
public class Post extends BaseTimeEntity {

    public static final int TITLE_MAX = 100;
    public static final int CONTENT_MAX = 5000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = TITLE_MAX)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private long likeCount;

    @Column(nullable = false)
    private long commentCount;

    @Column(nullable = false)
    private long viewCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status;

    public Post(Member member, String title, String content) {
        this.member = member;
        this.title = title;
        this.content = content;
        this.status = PostStatus.ACTIVE;
    }

    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public void delete() {
        this.status = PostStatus.DELETED;
    }

    public boolean isActive() {
        return status == PostStatus.ACTIVE;
    }

    /** member 프록시의 id 만 읽으므로 추가 쿼리가 나가지 않는다 */
    public boolean isWrittenBy(Long memberId) {
        return memberId != null && Objects.equals(member.getId(), memberId);
    }
}
