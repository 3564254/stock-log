package com.stocklog.modules.stock.briefing;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 종목 뉴스 AI 요약 */
@Entity
@Table(name = "stock_news_summary",
        uniqueConstraints = @UniqueConstraint(name = "uk_news_summary_code_date", columnNames = {"code", "summary_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "summary_date", nullable = false)
    private LocalDate summaryDate;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /** 요약에 쓴 기사 ID */
    @Column(name = "news_ids", length = 1000)
    private String newsIds;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public NewsSummary(String code, LocalDate summaryDate, String content, String newsIds, LocalDateTime createdAt) {
        this.code = code;
        this.summaryDate = summaryDate;
        this.content = content;
        this.newsIds = newsIds;
        this.createdAt = createdAt;
    }
}
