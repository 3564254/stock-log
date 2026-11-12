package com.stocklog.modules.stock.report;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 종목별 마지막 AI 리포트 */
@Entity
@Table(name = "stock_ai_report",
        uniqueConstraints = @UniqueConstraint(name = "uk_ai_report_code_date", columnNames = {"code", "report_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockAiReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    /** StockAiReport JSON */
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public StockAiReportEntity(String code, LocalDate reportDate, String content, LocalDateTime createdAt) {
        this.code = code;
        this.reportDate = reportDate;
        this.content = content;
        this.createdAt = createdAt;
    }

    public void replace(String content, LocalDateTime createdAt) {
        this.content = content;
        this.createdAt = createdAt;
    }
}
