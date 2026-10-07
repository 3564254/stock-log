package com.stocklog.modules.purchase.portfolio.entity;

import com.stocklog.modules.member.entity.Member;
import com.stocklog.global.common.base.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "portfolio_snapshot")
public class PortfolioSnapshot extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) // 스냅샷은 여러 개 생성될 수 있으므로 OneToOne보다 ManyToOne이 적합함
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    // 총 매수 금액 (투자 원금 개념)
    @Column(precision = 18, scale = 4)
    private BigDecimal totalPurchaseAmount;

    // 총 평가 금액 (현재가 기준 가치, 주식 자산만)
    @Column(precision = 18, scale = 4)
    private BigDecimal totalEvaluationAmount;

    // ✨ 거시적 자산 배분을 위한 핵심 추가 필드: 현금(예수금) 잔고
    @Column(precision = 18, scale = 4)
    private BigDecimal cashBalance;

    // 총 수익금
    @Column(precision = 18, scale = 4)
    private BigDecimal totalProfit;

    // 총 수익률
    @Column(precision = 10, scale = 4)
    private BigDecimal totalReturnRate;

    @Builder
    public PortfolioSnapshot(Member member, BigDecimal totalPurchaseAmount, BigDecimal totalEvaluationAmount, BigDecimal cashBalance, BigDecimal totalProfit, BigDecimal totalReturnRate) {
        this.member = member;
        this.totalPurchaseAmount = totalPurchaseAmount;
        this.totalEvaluationAmount = totalEvaluationAmount;
        this.cashBalance = cashBalance != null ? cashBalance : BigDecimal.ZERO;
        this.totalProfit = totalProfit;
        this.totalReturnRate = totalReturnRate;
    }

    // ✨ 총 자산 계산 (주식 평가액 + 예수금)
    public BigDecimal calculateTotalAssets() {
        return this.totalEvaluationAmount.add(this.cashBalance);
    }
}