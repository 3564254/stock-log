package com.stocklog.modules.market.chart;

import com.stocklog.modules.market.index.dto.vo.IndexChartData;
import com.stocklog.modules.market.index.entity.MarketIndexHistory;
import com.stocklog.modules.market.index.type.IndexType;
import lombok.Builder;

import java.util.List;

@Builder
public record MarketChartDto(
        IndexType indexType,
        List<IndexChartData> chartDataList
) {
    public static MarketChartDto createFallback() {
        return MarketChartDto.builder()
                .indexType(null)
                .chartDataList(List.of()).build();
    }

    public static MarketChartDto from(List<MarketIndexHistory> histories) {
        if (histories == null) return createFallback();

        MarketIndexHistory recentHistory = histories.get(0);

        return MarketChartDto.builder()
                .indexType(IndexType.fromSeriesId(recentHistory.getSymbol()))
                .chartDataList(histories.stream().map(IndexChartData::from).toList())
                .build();
    }
}
