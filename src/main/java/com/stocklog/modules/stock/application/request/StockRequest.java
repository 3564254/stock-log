package com.stocklog.modules.stock.application.request;

import com.stocklog.modules.stock.domain.type.MarketType;
import com.stocklog.modules.stock.stock.dto.StockDto;
import lombok.Builder;

@Builder
public record StockRequest(
        String code,
        MarketType marketType

) {
    public static StockRequest from(StockDto dto) {
        return StockRequest.builder()
                .code(dto.code())
                .marketType(dto.market())
                .build();
    }


}
