package com.stocklog.modules.stock.price;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stock_price")
public class StockPrice {

    @Id
    @Column(name = "stock_code", length = 20)
    private String code;


    // --- 📊 핵심 가격 정보 ---
    @Column(precision = 15, scale = 2)
    private BigDecimal currentPrice;   // 현재가 (기존 basePrice에서 직관적인 이름으로 변경)

    @Column(precision = 15, scale = 2)
    private BigDecimal previousClose;  // 전일종가 (이게 있어야 오늘 수익률/대비 계산이 가능합니다!)

    @Column(precision = 15, scale = 2)
    private BigDecimal openPrice;      // 당일 시가

    @Column(precision = 15, scale = 2)
    private BigDecimal highPrice;      // 당일 고가

    @Column(precision = 15, scale = 2)
    private BigDecimal lowPrice;       // 당일 저가

    // --- 📈 거래 및 시총 정보 ---
    @Column(precision = 20, scale = 0) // 거래량은 소수점이 없으므로 scale 0
    private BigDecimal volume;         // 누적 거래량

    @Column(precision = 25, scale = 4)
    private BigDecimal marketCap;      // 시가총액

    @Builder
    public StockPrice(String code, BigDecimal currentPrice, BigDecimal previousClose,
                      BigDecimal openPrice, BigDecimal highPrice, BigDecimal lowPrice,
                      BigDecimal volume, BigDecimal marketCap) {
        this.code = code;
        this.currentPrice = currentPrice;
        this.previousClose = previousClose;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.volume = volume;
        this.marketCap = marketCap;
    }

    // 장중에 가격 데이터가 통째로 갱신될 때 사용하는 메서드
    public void updatePrice(BigDecimal currentPrice, BigDecimal openPrice, BigDecimal highPrice,
                            BigDecimal lowPrice, BigDecimal volume, BigDecimal marketCap) {
        this.currentPrice = currentPrice;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.volume = volume;
        this.marketCap = marketCap;
        // 💡 주의: 전일종가(previousClose)는 장중에는 안 변하므로 업데이트 파라미터에서 뺐습니다.
    }
}