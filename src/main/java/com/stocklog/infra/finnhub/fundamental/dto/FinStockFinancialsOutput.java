package com.stocklog.infra.finnhub.fundamental.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FinStockFinancialsOutput {

    private String symbol;
    private List<FinFinancialPeriodData> data;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FinFinancialPeriodData {
        private int year;
        private int quarter;
        private String form;
        private String startDate;
        private String endDate;
        private FinFinancialStatements report;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FinFinancialStatements {
        @JsonProperty("bs")
        private List<FinReportItem> balanceSheet;

        @JsonProperty("cf")
        private List<FinReportItem> cashFlow;

        @JsonProperty("ic")
        private List<FinReportItem> incomeStatement;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FinReportItem {
        private String concept;
        private String label;
        private BigDecimal value;
    }
}