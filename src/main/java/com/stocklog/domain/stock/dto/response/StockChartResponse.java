package com.stocklog.domain.stock.dto.response;

import com.stocklog.domain.stock.dto.vo.price.StockChartData;
import lombok.Builder;
import java.util.List;

@Builder
public record StockChartResponse(
        String code,
        List<StockChartData> chartDataList
) {
    public static StockChartResponse createFallback() {
        return StockChartResponse.builder()
                .code("")
                .chartDataList(List.of())
                .build();
    }
}
