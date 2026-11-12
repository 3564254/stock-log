package com.stocklog.modules.stock.briefing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface NewsSummaryRepository extends JpaRepository<NewsSummary, Long> {
    Optional<NewsSummary> findByCodeAndSummaryDate(String code, LocalDate summaryDate);

    /** 가장 최근 요약 */
    Optional<NewsSummary> findTopByCodeOrderBySummaryDateDesc(String code);
}
