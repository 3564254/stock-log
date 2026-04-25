package com.stocklog.domain.stock.dto.vo.price;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class StockPrice {
    private BigDecimal currentPrice; // 현재가
    private BigDecimal changePrice;  // 전일 대비 금액
    private BigDecimal changeRate;   // 전일 대비율 (%)
    private BigDecimal basePrice;    // 기준가 (전일 종가 등)

    public static StockPrice createFallback() {
        return StockPrice.builder()
                .currentPrice(BigDecimal.ZERO)
                .changePrice(BigDecimal.ZERO)
                .changeRate(BigDecimal.ZERO)
                .basePrice(BigDecimal.ZERO)
                .build();
    }
}