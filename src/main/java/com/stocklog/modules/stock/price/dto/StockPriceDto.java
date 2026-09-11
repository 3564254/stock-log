package com.stocklog.modules.stock.price.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record StockPriceDto(
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
        ) {
    public static StockPriceDto createFallback() {
        return StockPriceDto.builder()
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

    public static StockPriceDto from(StockPriceResponse response) {
        return StockPriceDto.builder()
                .currentPrice(response.currentPrice())
                .changePrice(response.changePrice())
                .changeRate(response.changeRate())
                .basePrice(response.basePrice())
                .per(response.per())
                .pbr(response.pbr())
                .eps(response.eps())
                .bps(response.bps())
                .marketCap(response.marketCap())
                .w52High(response.w52High())
                .w52Low(response.w52Low())
                .volume(response.volume())
                .build();
    }
}
