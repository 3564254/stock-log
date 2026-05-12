package com.stocklog.domain.stock.price;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StockPriceRepository extends JpaRepository<StockPrice, String> {

}
