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
public class StockPriceDetail extends StockPrice {
    // [밸류에이션]
    private BigDecimal per;
    private BigDecimal pbr;
    private BigDecimal eps;
    private BigDecimal bps;

    // [시장 규모 및 기록]
    private BigDecimal marketCap;    // 시가총액
    private BigDecimal w52High;      // 52주 최고가
    private BigDecimal w52Low;       // 52주 최저가
    private BigDecimal volume;       // 누적 거래량

    public static StockPriceDetail createFallback() {
        return StockPriceDetail.builder()
                .currentPrice(BigDecimal.ZERO)
                .changePrice(BigDecimal.ZERO)
                .changeRate(BigDecimal.ZERO)
                .basePrice(BigDecimal.ZERO)
                .per(BigDecimal.ZERO)
                .pbr(BigDecimal.ZERO)
                .eps(BigDecimal.ZERO)
                .bps(BigDecimal.ZERO)
                .marketCap(BigDecimal.ZERO)
                .w52High(BigDecimal.ZERO)
                .w52Low(BigDecimal.ZERO)
                .volume(BigDecimal.ZERO)
                .build();
    }
}
