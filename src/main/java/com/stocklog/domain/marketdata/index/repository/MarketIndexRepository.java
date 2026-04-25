package com.stocklog.domain.marketdata.index.repository;

import com.stocklog.domain.marketdata.index.entity.MarketIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;

public interface MarketIndexRepository extends JpaRepository<MarketIndex, String> {


    MarketIndex findBySymbolAndUpdatedAt(String symbol, LocalDate localDate);
}
