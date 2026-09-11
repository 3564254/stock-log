package com.stocklog.modules.market.news;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NewsRepository extends JpaRepository<News, Long> {

    @Query("SELECT s.newsId FROM News s WHERE s.newsId IN :newsIds")
    List<String> findExistingNewsIds(@Param("newsIds") List<String> newsIds);

    @Query("SELECT s FROM News s WHERE s.related IS NULL OR s.related = ''")
    Page<News> findMarketNews(Pageable pageable);

    @Query("SELECT s FROM News s WHERE s.related = :code")
    Page<News> findStockNews(@Param("code") String code, Pageable pageable);

}
