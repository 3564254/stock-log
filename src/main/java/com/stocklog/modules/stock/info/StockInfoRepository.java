package com.stocklog.modules.stock.info;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StockInfoRepository extends JpaRepository<StockInfo, String > {

    @Query("SELECT s FROM StockInfo s " +
            "WHERE s.name LIKE UPPER(CONCAT('%',:keyword,'%')) OR " +
            "s.code LIKE UPPER(CONCAT('%',:keyword,'%')) " +
            "ORDER BY " +
            "CASE s.nation " +
            "WHEN 'US' THEN 1 " +
            "ELSE 2 " +
            "END ASC, " +
            "s.code ASC")
    Slice<StockInfo> findByKeyword(String keyword, Pageable pageable);
}
