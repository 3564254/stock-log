package com.stocklog.domain.portfolio.entity;


import com.stocklog.domain.member.entity.Member;
import com.stocklog.global.common.BaseTimeEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
public class PortfolioSnapshot extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    // 총 매수 금액 (투자 원금 개념)
    private BigDecimal totalPurchaseAmount;

    // 총 평가 금액 (현재가 기준 가치)
    private BigDecimal totalEvaluationAmount;

    // 총 수익금
    private BigDecimal totalProfit;

    // 총 수익률
    private BigDecimal totalReturnRate;
}
