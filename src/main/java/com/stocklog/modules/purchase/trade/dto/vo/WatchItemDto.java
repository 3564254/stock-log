package com.stocklog.modules.purchase.trade.dto.vo;


import com.stocklog.modules.common.type.ChartPhase;
import com.stocklog.modules.purchase.trade.entity.WatchItem;
import lombok.Builder;

@Builder
public record WatchItemDto(
        Long id,
        String stockCode,
        String stockName,
        String market,
        ChartPhase chartPhase,
        Double targetPrice
) {
    public static WatchItemDto of(WatchItem watchItem) {
        return new WatchItemDto(
                watchItem.getId(),
                watchItem.getStock().getCode(),
                watchItem.getStock().getName(),
                watchItem.getStock().getMarketType().name(),
                watchItem.getChartPhase(),
                watchItem.getTargetPrice()
        );
    }
}
