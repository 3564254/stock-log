package com.stocklog.modules.stock.price;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockPriceRepository extends JpaRepository<StockPrice, String> {
    List<StockPrice> findByCodeIn(List<String> codes);
}
