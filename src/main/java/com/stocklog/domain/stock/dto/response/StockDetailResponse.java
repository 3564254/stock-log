package com.stocklog.domain.stock.dto.response;

import com.stocklog.domain.stock.dto.vo.info.StockInfoDetail;
import com.stocklog.domain.stock.dto.vo.price.StockPriceDetail;
import com.stocklog.domain.stock.entity.Stock;
import lombok.Builder;

@Builder
public record StockDetailResponse(
        StockResponse stock,    // 종목 기본 정보
        StockInfoDetail info,   // 기업 정보 상세
        StockPriceDetail price  // 실시간 시세 상세
) {
    public static StockDetailResponse of(Stock stock, StockInfoDetail info, StockPriceDetail price) {
        return new StockDetailResponse(StockResponse.from(stock), info, price);
    }

    public static StockDetailResponse createFallback() {
        return new StockDetailResponse(StockResponse.createFallback(), StockInfoDetail.createDetailFallback(), StockPriceDetail.createFallback());
    }
}
