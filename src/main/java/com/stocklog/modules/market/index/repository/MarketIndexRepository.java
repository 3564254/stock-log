package com.stocklog.modules.market.index.repository;

import com.stocklog.modules.market.index.entity.MarketIndex;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface MarketIndexRepository extends JpaRepository<MarketIndex, String> {


    MarketIndex findBySymbolAndUpdatedAt(String symbol, LocalDate localDate);
}
