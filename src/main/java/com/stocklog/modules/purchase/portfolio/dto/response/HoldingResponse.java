package com.stocklog.modules.purchase.portfolio.dto.response;

import com.stocklog.modules.purchase.portfolio.entity.Holding;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record HoldingResponse(
        Long holdingId,
        String stockCode,
        String korName,
        String sector,
        BigDecimal averagePrice,
        BigDecimal currentPrice,
        BigDecimal highestPriceSinceBuy,
        BigDecimal returnRate // 수익률(%)
) {
    public static HoldingResponse of(Holding holding, BigDecimal currentPrice) {
        return HoldingResponse.builder()
                .holdingId(holding.getId())
                .stockCode(holding.getStock().getCode())
                .korName(holding.getStock().getName())
                .sector(holding.getStock().getSector())
                .averagePrice(holding.getAveragePrice())
                .currentPrice(currentPrice)
                .highestPriceSinceBuy(holding.getHighestPriceSinceBuy())
                .returnRate(holding.calculateReturnRate(currentPrice))
                .build();
    }
}