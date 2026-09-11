package com.stocklog.modules.purchase.trade.dto.vo;


import com.stocklog.modules.purchase.trade.entity.WatchItem;
import com.stocklog.modules.stock.analysis.domain.signal.vo.MarketPhase;
import lombok.Builder;

@Builder
public record WatchItemDto(
        Long id,
        String stockCode,
        String stockName,
        String market,
        MarketPhase marketPhase,
        Double targetPrice
) {
    public static WatchItemDto of(WatchItem watchItem) {
        return new WatchItemDto(
                watchItem.getId(),
                watchItem.getStock().getCode(),
                watchItem.getStock().getName(),
                watchItem.getStock().getMarketType().name(),
                watchItem.getMarketPhase(),
                watchItem.getTargetPrice()
        );
    }
}
