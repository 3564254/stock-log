package com.stocklog.domain.stock.dto.request;

import com.stocklog.domain.stock.type.MarketType;

import java.time.LocalDate;

public record ChartRequest(
        String code,
        MarketType marketType,
        String localDate
) {

}