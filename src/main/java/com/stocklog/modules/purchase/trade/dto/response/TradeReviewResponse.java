package com.stocklog.modules.purchase.trade.dto.response;

import com.stocklog.modules.purchase.trade.type.TradeEmotion;
import com.stocklog.modules.purchase.trade.entity.TradeReview;
import java.time.LocalDateTime;

public record TradeReviewResponse(
        Long id,
        Long tradeLogId,
        String stockCode,
        String title,
        String content,
        TradeEmotion emotion,
        String emotionDescription,
        Integer score,
        LocalDateTime createdAt
) {
    public static TradeReviewResponse from(TradeReview review) {
        return new TradeReviewResponse(
                review.getId(),
                review.getTradeLogId(),
                review.getStockCode(),
                review.getTitle(),
                review.getContent(),
                review.getEmotion(),
                review.getEmotion() != null ? review.getEmotion().getDescription() : "선택 안 함",
                review.getScore(),
                review.getCreatedAt()
        );
    }
}