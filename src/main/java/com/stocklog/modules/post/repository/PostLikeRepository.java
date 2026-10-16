package com.stocklog.modules.post.repository;

import com.stocklog.modules.post.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    @Query("select count(l) > 0 from PostLike l where l.post.id = :postId and l.member.id = :memberId")
    boolean existsByPostIdAndMemberId(@Param("postId") Long postId, @Param("memberId") Long memberId);

    /** 피드에서 "내가 누른 글" 표시용. 글마다 exists 를 부르지 않고 IN 한 번으로 */
    @Query("select l.post.id from PostLike l where l.member.id = :memberId and l.post.id in :postIds")
    List<Long> findLikedPostIds(@Param("memberId") Long memberId, @Param("postIds") Collection<Long> postIds);

    /**
     * 좋아요 취소. 조회 후 delete 하지 않고 한 문장으로 지워서, 지운 행 수(0 또는 1)로 "눌려 있었는지"를 판단한다.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from PostLike l where l.post.id = :postId and l.member.id = :memberId")
    int deleteByPostIdAndMemberId(@Param("postId") Long postId, @Param("memberId") Long memberId);
}
