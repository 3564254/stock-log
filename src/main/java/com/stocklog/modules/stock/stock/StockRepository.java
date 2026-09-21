package com.stocklog.modules.stock.stock;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StockRepository extends JpaRepository<Stock, String> {


    @Query("SELECT s FROM Stock s " +
            "WHERE UPPER(s.name) LIKE UPPER(CONCAT('%', :keyword, '%')) OR " +
            "UPPER(s.code) LIKE UPPER(CONCAT('%', :keyword, '%')) " +
            "ORDER BY CASE WHEN UPPER(s.code) = UPPER(:keyword) THEN 0 " +
            "WHEN UPPER(s.name) = UPPER(:keyword) THEN 0 " +
            "ELSE 1 END ASC, " +
            "LENGTH(s.name) ASC, " +
            "CASE WHEN s.nation = 'US' THEN 1 ELSE 2 END ASC, " +
            "s.code ASC")
    Slice<Stock> findByKeyword(String keyword, Pageable pageable);
}
