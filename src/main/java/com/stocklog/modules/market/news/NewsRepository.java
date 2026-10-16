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

    /** 출처별 시장 뉴스 (종목 태그가 없는 기사) */
    @Query("SELECT s FROM News s WHERE s.provider = :provider AND (s.related IS NULL OR s.related = '')")
    Page<News> findMarketNewsByProvider(@Param("provider") NewsSource provider, Pageable pageable);

    /** 출처별 최신 N건 (종목 기사 포함) — PageRequest.of(0, n)으로 개수 제한 */
    List<News> findByProviderOrderByDateTimeDesc(NewsSource provider, Pageable pageable);

    @Query("SELECT s FROM News s WHERE s.related = :code")
    Page<News> findStockNews(@Param("code") String code, Pageable pageable);

    @Query(value = "SELECT * FROM stock_news ORDER BY date_time DESC LIMIT :limit", nativeQuery = true)
    List<News> findNewsDESCTopN(@Param("limit") int limit);

}
