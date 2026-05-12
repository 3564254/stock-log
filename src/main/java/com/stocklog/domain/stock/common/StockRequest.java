package com.stocklog.domain.stock.common;

import com.stocklog.domain.stock.common.type.MarketType;
import com.stocklog.domain.stock.stock.dto.StockDto;
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
