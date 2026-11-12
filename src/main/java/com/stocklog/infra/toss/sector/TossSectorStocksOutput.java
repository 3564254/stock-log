package com.stocklog.infra.toss.sector;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/** 토스 섹터 구성 종목 응답 result */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossSectorStocksOutput(List<SectorStock> stocks) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SectorStock(
            String symbol,
            String name,
            BigDecimal volume,
            BigDecimal tradingAmount,
            BigDecimal marketCapitalization,
            String currency
    ) {
    }
}
