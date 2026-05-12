package com.stocklog.infra.finnhub.fundamental.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class FinStockFinancialsOutput {
    private String symbol; // API 응답에 티커가 포함되는 경우

    @JsonProperty("metric")
    private Metric metric;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Metric {
        // --- 수익성 ---
        @JsonProperty("grossMarginTTM")
        private BigDecimal grossMarginTTM;

        @JsonProperty("operatingMarginTTM")
        private BigDecimal operatingMarginTTM;

        @JsonProperty("netProfitMarginTTM")
        private BigDecimal netProfitMarginTTM;

        @JsonProperty("roeTTM")
        private BigDecimal roeTTM;

        @JsonProperty("roaTTM")
        private BigDecimal roaTTM;

        // --- 성장성 ---
        @JsonProperty("revenueGrowthTTMYoy")
        private BigDecimal revenueGrowthTTMYoy;

        @JsonProperty("epsGrowthTTMYoy")
        private BigDecimal epsGrowthTTMYoy;

        // --- 배당 ---
        @JsonProperty("currentDividendYieldTTM")
        private BigDecimal dividendYieldTTM;

        @JsonProperty("payoutRatioTTM")
        private BigDecimal payoutRatioTTM;

        // --- 안정성 및 효율성 ---
        @JsonProperty("currentRatioQuarterly")
        private BigDecimal currentRatioQuarterly;

        @JsonProperty("totalDebt/totalEquityQuarterly")
        private BigDecimal debtToEquityQuarterly;

        @JsonProperty("inventoryTurnoverTTM")
        private BigDecimal inventoryTurnoverTTM;

        // --- 기타 (필요시 추가) ---
        @JsonProperty("52WeekHigh")
        private BigDecimal high52Week;

        @JsonProperty("52WeekLow")
        private BigDecimal low52Week;
    }
}
