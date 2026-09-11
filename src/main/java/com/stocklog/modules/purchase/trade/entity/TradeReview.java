package com.stocklog.modules.purchase.trade.entity;

import com.stocklog.modules.purchase.trade.type.TradeEmotion;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "trade_review_memo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradeReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✨ 회원 식별자 추가 (누구의 자유 복기 메모인지 구분하기 위함)
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "trade_log_id")
    private Long tradeLogId;

    @Column(name = "stock_code")
    private String stockCode;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "emotion", length = 50)
    private TradeEmotion emotion;

    @Column(name = "score")
    private Integer score;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Builder
    public TradeReview(Long id, Long memberId, Long tradeLogId, String stockCode, String title, String content, TradeEmotion emotion, Integer score, LocalDateTime createdAt) {
        this.id = id;
        this.memberId = memberId; // ✨ 빌더 및 생성자에 추가
        this.tradeLogId = tradeLogId;
        this.stockCode = stockCode;
        this.title = title;
        this.content = content;
        this.emotion = emotion;
        this.score = score;
        this.createdAt = createdAt;
    }

    public void update(String title, String content, TradeEmotion emotion, Integer score) {
        this.title = title;
        this.content = content;
        this.emotion = emotion;
        this.score = score;
    }
}