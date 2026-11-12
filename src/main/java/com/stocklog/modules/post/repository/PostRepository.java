package com.stocklog.modules.post.repository;

import com.stocklog.modules.post.entity.Post;
import com.stocklog.modules.post.entity.PostStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    /** 전체 피드 (최신순, 커서). 첫 페이지는 cursor = Long.MAX_VALUE */
    @Query("select p from Post p join fetch p.member " +
            "where p.status = :status and p.id < :cursor " +
            "order by p.id desc")
    List<Post> findFeed(@Param("status") PostStatus status,
                        @Param("cursor") Long cursor,
                        Pageable pageable);

    /** 종목별 피드. post_stock_tag 의 인덱스로 글 id 를 최신순으로 읽고 post 와 붙인다 */
    @Query("select p from PostStockTag t join t.post p join fetch p.member " +
            "where t.stock.code = :stockCode and p.status = :status and t.post.id < :cursor " +
            "order by t.post.id desc")
    List<Post> findFeedByStock(@Param("stockCode") String stockCode,
                               @Param("status") PostStatus status,
                               @Param("cursor") Long cursor,
                               Pageable pageable);

    @Query("select p from Post p join fetch p.member where p.id = :id")
    Optional<Post> findWithMemberById(@Param("id") Long id);

    @Query("select p.likeCount from Post p where p.id = :id")
    long findLikeCount(@Param("id") Long id);

    // 카운터: 읽고 +1 해서 저장하면 동시 요청에 값이 덮어써진다

    @Modifying(flushAutomatically = true)
    @Query("update Post p set p.likeCount = p.likeCount + 1 where p.id = :id")
    int increaseLikeCount(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query("update Post p set p.likeCount = p.likeCount - 1 where p.id = :id and p.likeCount > 0")
    int decreaseLikeCount(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query("update Post p set p.commentCount = p.commentCount + 1 where p.id = :id")
    int increaseCommentCount(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query("update Post p set p.commentCount = p.commentCount - 1 where p.id = :id and p.commentCount > 0")
    int decreaseCommentCount(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query("update Post p set p.viewCount = p.viewCount + 1 where p.id = :id and p.status = :status")
    int increaseViewCount(@Param("id") Long id, @Param("status") PostStatus status);
}
