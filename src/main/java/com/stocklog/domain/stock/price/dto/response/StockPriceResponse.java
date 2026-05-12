package com.stocklog.domain.stock.price.dto.response;

import lombok.Builder;
import com.stocklog.global.util.FinancialConst;

import java.math.BigDecimal;
@Builder
public record StockPriceResponse (
        BigDecimal currentPrice,
        BigDecimal changePrice,
        BigDecimal changeRate,
        BigDecimal basePrice,

        BigDecimal per,
        BigDecimal pbr,
        BigDecimal eps,
        BigDecimal bps,

        BigDecimal marketCap,
        BigDecimal w52High,
        BigDecimal w52Low,
        BigDecimal volume

){
    public static StockPriceResponse createFallback() {
        return StockPriceResponse.builder()
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
