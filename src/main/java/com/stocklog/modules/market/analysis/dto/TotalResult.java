package com.stocklog.modules.market.analysis.dto;

import lombok.Getter;


public record TotalResult(
        EngineInput engineInput,
        MarketRegimeDto marketRegimeDto
) {
}
