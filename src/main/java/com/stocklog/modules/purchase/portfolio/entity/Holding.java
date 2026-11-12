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
import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "holding", uniqueConstraints = {
        @UniqueConstraint(name = "uk_member_stock", columnNames = {"member_id", "stock_code"})
})
public class Holding extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // TODO: 평균 매입가 동시 수정 대비 (낙관적 락)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_code", nullable = false)
    private StockInfo stock;

    @Column(precision = 18, scale = 4)
    private BigDecimal averagePrice; // 평균 매수 단가

    // 보유 수량 (선택 입력). 소수점 거래를 고려해 소수 6자리까지
    @Column(precision = 18, scale = 6)
    private BigDecimal quantity;

    @Column(precision = 18, scale = 4)
    private BigDecimal highestPriceSinceBuy; // 트레이링 스톱(고점 대비 하락) 전략용 추적 필드

    // 매수(또는 평단 변경) 시점에 타점 엔진이 낸 진입 계획의 구조적 손절가. 계획이 없을 때 산 종목은 null
    @Column(name = "entry_stop", precision = 18, scale = 4)
    private BigDecimal entryStop;

    // 지금 평단으로 보유를 시작한 날. 매수 후 최고가를 이날 이후 종가로 계산한다
    @Column(name = "basis_date")
    private LocalDate basisDate;

    // 보유 방식. null = 기본
    @Column(name = "hold_style", length = 20)
    private String holdStyle;

    @Builder
    public Holding(Member member, StockInfo stock, BigDecimal averagePrice, BigDecimal quantity, BigDecimal highestPriceSinceBuy,
                   BigDecimal entryStop) {
        this.member = member;
        this.stock = stock;
        this.averagePrice = averagePrice;
        this.quantity = quantity;
        this.highestPriceSinceBuy = highestPriceSinceBuy;
        this.entryStop = entryStop;
        this.basisDate = LocalDate.now();
    }

    /** 지금 평단의 보유 시작일 */
    public LocalDate basisDateOrCreated() {
        if (basisDate != null) return basisDate;
        return getCreatedDate() != null ? getCreatedDate().toLocalDate() : null;
    }

    /** 평단이 바뀌면(추가 매수 등) 새 포지션으로 보고 최고가와 매수 시점 손절가를 다시 잡는다 */
    public void resetForNewBasis(BigDecimal newAveragePrice, BigDecimal newEntryStop) {
        this.averagePrice = newAveragePrice;
        this.highestPriceSinceBuy = newAveragePrice;
        this.entryStop = newEntryStop;
        this.basisDate = LocalDate.now();
    }

    /** 보유 방식 바꾸기. "MOMENTUM"이 아니면 기본(null) */
    public void updateHoldStyle(String style) {
        this.holdStyle = "MOMENTUM".equalsIgnoreCase(style == null ? "" : style.trim()) ? "MOMENTUM" : null;
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
