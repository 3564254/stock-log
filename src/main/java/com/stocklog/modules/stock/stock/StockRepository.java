package com.stocklog.modules.stock.stock;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StockRepository extends JpaRepository<Stock, String> {


    @Query("SELECT s FROM Stock s " +
            "WHERE s.name LIKE UPPER(CONCAT('%',:keyword,'%')) OR " +
            "s.code LIKE UPPER(CONCAT('%',:keyword,'%')) " +
            "ORDER BY " +
            "CASE s.nation " +
            "WHEN 'US' THEN 1 " +
            "ELSE 2 " +
            "END ASC, " +
            "s.code ASC")
    Slice<Stock> findByKeyword(String keyword, Pageable pageable);
}
