package com.stocklog.modules.market.newssector;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 하루 한 번 받은 뉴스의 섹터 영향 AI 판단 (회원 공통) */
@Entity
@Table(name = "news_sector_impact", uniqueConstraints = @UniqueConstraint(name = "uk_news_sector_day", columnNames = "day"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsSectorEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "day", nullable = false)
    private LocalDate day;

    /** AI 판단 원문(JSON)과 당시 섹터 ETF 가격 */
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public NewsSectorEntity(LocalDate day, String content, LocalDateTime createdAt) {
        this.day = day;
        this.content = content;
        this.createdAt = createdAt;
    }
}
