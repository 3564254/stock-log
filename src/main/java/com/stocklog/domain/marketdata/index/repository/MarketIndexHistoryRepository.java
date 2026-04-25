package com.stocklog.domain.marketdata.index.repository;

import com.stocklog.domain.marketdata.index.entity.MarketIndexHistory;
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
}
