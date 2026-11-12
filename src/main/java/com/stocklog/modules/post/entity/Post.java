package com.stocklog.modules.post.entity;

import com.stocklog.global.common.base.BaseTimeEntity;
import com.stocklog.modules.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/** 커뮤니티 게시글. - 작성자(Member)만 단방향으로 참조한다 */
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
