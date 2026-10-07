package com.stocklog.modules.purchase.portfolio.entity;

import com.stocklog.modules.member.entity.Member;
import com.stocklog.modules.stock.info.StockInfo;
import com.stocklog.global.common.base.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "holding", uniqueConstraints = {
        @UniqueConstraint(name = "uk_member_stock", columnNames = {"member_id", "stock_code"})
})
public class Holding extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // TODO(JPA): 평균 매입가는 여러 요청이 동시에 수정할 수 있는 값이다.
    // 낙관적 락(@Version)을 도입하고 OptimisticLockException 발생 시의 재시도/사용자 안내 정책을 설계해 보자.

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_code", nullable = false)
    private StockInfo stock;

    @Column(precision = 18, scale = 4)
    private BigDecimal averagePrice; // 수량 제거 후 오직 전략 기준선이 되는 '평균 단가'만 유지

    @Column(precision = 18, scale = 4)
    private BigDecimal highestPriceSinceBuy; // 트레이링 스톱(고점 대비 하락) 전략용 추적 필드

    @Builder
    public Holding(Member member, StockInfo stock, BigDecimal averagePrice, BigDecimal highestPriceSinceBuy) {
        this.member = member;
        this.stock = stock;
        this.averagePrice = averagePrice;
        this.highestPriceSinceBuy = highestPriceSinceBuy;
    }

    // 물타기(추가 매수) 등 발생 시 평단가만 갱신
    public void updateAveragePrice(BigDecimal newAveragePrice) {
        this.averagePrice = newAveragePrice;
    }

    // AI 엔진 스캔 시 호출될 최고점 동적 갱신 로직
    public void updateHighestPrice(BigDecimal currentPrice) {
        if (this.highestPriceSinceBuy == null || currentPrice.compareTo(this.highestPriceSinceBuy) > 0) {
            this.highestPriceSinceBuy = currentPrice;
        }
    }

    // ❌ 절대 금액(수익금) 계산 로직 전면 삭제
    // ✅ 오직 전략 검증을 위한 수익률(%)만 반환
    public BigDecimal calculateReturnRate(BigDecimal currentPrice) {
        if (this.averagePrice == null || this.averagePrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return currentPrice.subtract(this.averagePrice)
                .divide(this.averagePrice, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }
}
