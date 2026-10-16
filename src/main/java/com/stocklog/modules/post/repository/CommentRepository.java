package com.stocklog.modules.post.repository;

import com.stocklog.modules.post.entity.Comment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** 글의 댓글(대댓글 제외)을 오래된 순으로, cursor 다음부터. 첫 페이지는 cursor = 0 */
    @Query("select c from Comment c join fetch c.member " +
            "where c.post.id = :postId and c.parent is null and c.id > :cursor " +
            "order by c.id asc")
    List<Comment> findRootComments(@Param("postId") Long postId,
                                   @Param("cursor") Long cursor,
                                   Pageable pageable);

    /** 위에서 가져온 댓글들의 대댓글을 한 번에 */
    @Query("select c from Comment c join fetch c.member " +
            "where c.parent.id in :parentIds order by c.id asc")
    List<Comment> findRepliesByParentIdIn(@Param("parentIds") Collection<Long> parentIds);

    @Query("select c from Comment c join fetch c.post where c.id = :id")
    Optional<Comment> findWithPostById(@Param("id") Long id);
}
