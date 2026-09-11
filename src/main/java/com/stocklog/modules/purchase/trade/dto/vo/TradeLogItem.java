package com.stocklog.modules.purchase.trade.dto.vo;

import com.stocklog.modules.purchase.trade.entity.TradeLog;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record TradeLogItem(
        Long id,
        String korName,
        String stockCode,
        BigDecimal quantity,
        BigDecimal profit,
        boolean isProfit
) {

    public static TradeLogItem from(TradeLog tradeLog) {
        return TradeLogItem.builder()
                .id(tradeLog.getId())
                .korName(tradeLog.getStock().getName())
                .stockCode(tradeLog.getStock().getCode())
                .quantity(tradeLog.getQuantity())
                .profit(tradeLog.getProfit())
                .isProfit(tradeLog.getProfit().compareTo(BigDecimal.ZERO) > 0)
                .build();
    }
}