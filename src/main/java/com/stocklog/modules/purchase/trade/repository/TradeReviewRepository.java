package com.stocklog.modules.purchase.trade.repository;

import com.stocklog.modules.purchase.trade.entity.TradeReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TradeReviewRepository extends JpaRepository<TradeReview, Long> {
    Optional<TradeReview> findByTradeLogId(Long tradeLogId);
    boolean existsByTradeLogId(Long tradeLogId);
    List<TradeReview> findAllByMemberIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long memberId, LocalDateTime start, LocalDateTime end);
}
