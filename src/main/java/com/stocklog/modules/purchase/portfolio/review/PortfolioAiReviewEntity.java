package com.stocklog.modules.purchase.portfolio.review;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 회원별 AI 주간 점검 */
@Entity
@Table(name = "portfolio_ai_review",
        uniqueConstraints = @UniqueConstraint(name = "uk_pf_review_member_week", columnNames = {"member_id", "week_start"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioAiReviewEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    /** PortfolioAiReview JSON */
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /** 이때 AI에 넘긴 사실(금액 없음). 다음 주 '지난주 대비'에 쓴다 */
    @Column(name = "facts", columnDefinition = "TEXT", nullable = false)
    private String facts;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public PortfolioAiReviewEntity(Long memberId, LocalDate weekStart, String content, String facts, LocalDateTime createdAt) {
        this.memberId = memberId;
        this.weekStart = weekStart;
        this.content = content;
        this.facts = facts;
        this.createdAt = createdAt;
    }
}
