package com.stocklog.modules.stock.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface StockAiReportRepository extends JpaRepository<StockAiReportEntity, Long> {
    Optional<StockAiReportEntity> findByCodeAndReportDate(String code, LocalDate reportDate);

    Optional<StockAiReportEntity> findTopByCodeOrderByReportDateDesc(String code);
}
