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
    private BigDecimal averagePrice; // 평균 매수 단가 (수익률·전략 검증의 기준선)

    // 보유 수량 (선택 입력). 소수점 거래를 고려해 소수 6자리까지.
    // null이면 평가금액·비중 계산에서 빠지고 수익률만 보여 준다.
    @Column(precision = 18, scale = 6)
    private BigDecimal quantity;

    @Column(precision = 18, scale = 4)
    private BigDecimal highestPriceSinceBuy; // 트레이링 스톱(고점 대비 하락) 전략용 추적 필드

    @Builder
    public Holding(Member member, StockInfo stock, BigDecimal averagePrice, BigDecimal quantity, BigDecimal highestPriceSinceBuy) {
        this.member = member;
        this.stock = stock;
        this.averagePrice = averagePrice;
        this.quantity = quantity;
        this.highestPriceSinceBuy = highestPriceSinceBuy;
    }

    // 물타기(추가 매수) 등 발생 시 평단가만 갱신
    public void updateAveragePrice(BigDecimal newAveragePrice) {
        this.averagePrice = newAveragePrice;
    }

    // 수량은 선택값이라 null(입력 안 함)로 되돌릴 수도 있다
    public void updateQuantity(BigDecimal newQuantity) {
        this.quantity = newQuantity;
    }

    // AI 엔진 스캔 시 호출될 최고점 동적 갱신 로직
    public void updateHighestPrice(BigDecimal currentPrice) {
        if (this.highestPriceSinceBuy == null || currentPrice.compareTo(this.highestPriceSinceBuy) > 0) {
            this.highestPriceSinceBuy = currentPrice;
        }
    }

    // 전략 검증용 수익률(%)
    public BigDecimal calculateReturnRate(BigDecimal currentPrice) {
        if (currentPrice == null) {
            return null; // 현재가를 못 가져온 경우: 화면에서 '-'
        }
        if (this.averagePrice == null || this.averagePrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return currentPrice.subtract(this.averagePrice)
                .divide(this.averagePrice, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }

    // 평가금액 = 수량 × 현재가 (종목 통화 기준). 수량이나 현재가가 없으면 null
    public BigDecimal calculateMarketValue(BigDecimal currentPrice) {
        if (this.quantity == null || currentPrice == null) {
            return null;
        }
        return currentPrice.multiply(this.quantity);
    }
}
