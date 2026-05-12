package com.stocklog.domain.stock.chart.dto.request;

import com.stocklog.domain.stock.common.type.MarketType;
import lombok.Builder;

@Builder
public record ChartRequest(
        String code,
        MarketType marketType,
        String localDate
) {

}