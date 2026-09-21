package com.stocklog.modules.market.index.repository;

import com.stocklog.modules.market.index.entity.MarketIndexHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MarketIndexHistoryRepository extends JpaRepository<MarketIndexHistory, Long> {
    List<MarketIndexHistory> findBySymbolOrderByDateAsc(String symbol);

    @Query("SELECT MAX(h.date) FROM MarketIndexHistory h WHERE h.symbol = :symbol")
    Optional<LocalDate> findMaxDateBySymbol(@Param("symbol") String symbol);

    // 단일 심볼의 최근 N개 조회 (Pageable 없이 Limit 사용 시 Native Query 권장)
    @Query(value = "SELECT * FROM market_index_history WHERE symbol = :symbol ORDER BY date DESC LIMIT :n", nativeQuery = true)
    List<MarketIndexHistory> findHistoryDESCTopN(@Param("symbol") String symbol, @Param("n") int n);

    // [핵심] Facade용 단일 쿼리: 여러 심볼의 특정 날짜 이후 데이터를 날짜 오름차순으로 한 번에 조회
    @Query("SELECT h FROM MarketIndexHistory h WHERE h.symbol IN :symbols AND h.date >= :startDate ORDER BY h.date ASC")
    List<MarketIndexHistory> findRecentDataBySymbols(
            @Param("symbols") List<String> symbols,
            @Param("startDate") LocalDate startDate
    );
}