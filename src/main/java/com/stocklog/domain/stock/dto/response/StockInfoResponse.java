package com.stocklog.domain.stock.dto.response;

import com.stocklog.domain.stock.dto.vo.info.StockInfoDetail;
import lombok.Builder;


@Builder
public record StockInfoResponse(
        StockResponse stock,

        StockInfoDetail info

) {
    public static StockInfoResponse of(StockResponse stock, StockInfoDetail info) {
        return StockInfoResponse.builder()
                .stock(stock)
                .info(info)
                .build();
    }

    public static StockInfoResponse createFallback() {
        return StockInfoResponse.builder()
                .stock(StockResponse.createFallback())
                .info(StockInfoDetail.createDetailFallback())
                .build();
    }
}