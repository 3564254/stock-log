package com.stocklog.modules.stock.chart.dto;

import com.stocklog.modules.stock.domain.type.MarketType;
import lombok.Builder;

@Builder
public record ChartRequest(
        String code,
        MarketType marketType,
        String localDate
) {

}