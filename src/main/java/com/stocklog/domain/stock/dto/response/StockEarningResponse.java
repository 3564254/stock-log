package com.stocklog.domain.stock.dto.response;

import com.stocklog.domain.stock.dto.vo.fundamental.earning.EarningSurpriseData;
import lombok.Builder;

import java.util.List;

@Builder
public record StockEarningResponse(
        List<EarningSurpriseData> data
) {
    public static StockEarningResponse createFallback() {
        return StockEarningResponse.builder()
                .data(List.of())
                .build();
    }
}