package com.stocklog.modules.post.repository;

import com.stocklog.modules.post.entity.PostStockTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PostStockTagRepository extends JpaRepository<PostStockTag, Long> {

    /** 피드 한 페이지에 보이는 글들의 태그를 한 번에 (글마다 조회하면 N+1) */
    @Query("select t from PostStockTag t join fetch t.stock " +
            "where t.post.id in :postIds order by t.id asc")
    List<PostStockTag> findAllWithStockByPostIdIn(@Param("postIds") Collection<Long> postIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from PostStockTag t where t.post.id = :postId")
    int deleteAllByPostId(@Param("postId") Long postId);
}
