package com.stocklog.modules.purchase.portfolio.review;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface PortfolioAiReviewRepository extends JpaRepository<PortfolioAiReviewEntity, Long> {
    Optional<PortfolioAiReviewEntity> findByMemberIdAndWeekStart(Long memberId, LocalDate weekStart);

    Optional<PortfolioAiReviewEntity> findTopByMemberIdOrderByWeekStartDesc(Long memberId);

    Optional<PortfolioAiReviewEntity> findTopByMemberIdAndWeekStartBeforeOrderByWeekStartDesc(Long memberId, LocalDate weekStart);
}
