package com.stocklog.modules.market.chart;

import com.stocklog.modules.market.index.dto.IndexChartDto;
import com.stocklog.modules.market.index.entity.MarketIndexHistory;
import com.stocklog.modules.market.domain.type.IndexType;
import lombok.Builder;

import java.util.List;

@Builder
public record MarketChartData(
        IndexType indexType,
        List<IndexChartDto> chartDataList
) {
    public static MarketChartData createFallback() {
        return MarketChartData.builder()
                .indexType(null)
                .chartDataList(List.of()).build();
    }

    public static MarketChartData from(List<MarketIndexHistory> histories) {
        if (histories == null) return createFallback();

        MarketIndexHistory recentHistory = histories.get(0);

        return MarketChartData.builder()
                .indexType(IndexType.fromSeriesId(recentHistory.getSymbol()))
                .chartDataList(histories.stream().map(IndexChartDto::from).toList())
                .build();
    }
}
