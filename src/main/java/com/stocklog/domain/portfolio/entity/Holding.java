package com.stocklog.domain.portfolio.entity;


import com.stocklog.domain.member.entity.Member;
import com.stocklog.domain.stock.common.type.Currency;
import com.stocklog.domain.stock.info.StockInfo;
import com.stocklog.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.stocklog.global.util.FinancialConst;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_code", nullable = false)
    private StockInfo stock;

    private BigDecimal quantity;
    private BigDecimal purchasePrice;         // 현지 통화 기준 매수 단가
    private BigDecimal purchaseExchangeRate;  // 매수 당시 환율

    @Enumerated(EnumType.STRING)
    private Currency currency;

    @Builder
    public Holding(Member member, StockInfo stock, BigDecimal quantity, BigDecimal purchasePrice, BigDecimal purchaseExchangeRate, Currency currency) {
        this.member = member;
        this.stock = stock;
        this.quantity = quantity;
        this.purchasePrice = purchasePrice;
        this.purchaseExchangeRate = purchaseExchangeRate;
        this.currency = currency;
    }

    public void updateHolding(BigDecimal newQuantity, BigDecimal newPurchasePrice, BigDecimal newExchangeRate) {
        this.quantity = newQuantity;
        this.purchasePrice = newPurchasePrice;
        this.purchaseExchangeRate = newExchangeRate;
    }

    // 원화 환산 매수 총액 계산
    public BigDecimal calculatePurchaseValueKrw() {
        BigDecimal price = (this.purchasePrice != null) ? this.purchasePrice : BigDecimal.ZERO;
        BigDecimal rate = (this.purchaseExchangeRate != null) ? this.purchaseExchangeRate : BigDecimal.ONE;
        BigDecimal qty = (this.quantity != null) ? this.quantity : BigDecimal.ZERO;

        return price.multiply(rate).multiply(qty);
    }

    // 원화 환산 현재 평가액 계산
    public BigDecimal calculateCurrentValueKrw(BigDecimal currentPrice, BigDecimal currentExchangeRate) {
        BigDecimal price = (currentPrice != null) ? currentPrice : BigDecimal.ZERO;
        BigDecimal rate = (currentExchangeRate != null) ? currentExchangeRate : BigDecimal.ONE;
        BigDecimal qty = (this.quantity != null) ? this.quantity : BigDecimal.ZERO;

        return price.multiply(rate).multiply(qty);
    }

    // 수익금 계산 (helper 메서드 활용)
    public BigDecimal calculateProfit(BigDecimal currentPrice, BigDecimal currentExchangeRate) {
        return calculateCurrentValueKrw(currentPrice, currentExchangeRate)
                .subtract(calculatePurchaseValueKrw());
    }

    // 수익률 계산 (helper 메서드 활용)
    public BigDecimal calculateProfitRate(BigDecimal currentPrice, BigDecimal currentExchangeRate) {
        BigDecimal totalPurchaseKrw = calculatePurchaseValueKrw();

        if (totalPurchaseKrw.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;

        return calculateProfit(currentPrice, currentExchangeRate)
                .divide(totalPurchaseKrw, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }
}