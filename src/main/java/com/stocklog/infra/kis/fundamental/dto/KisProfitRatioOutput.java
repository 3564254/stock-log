package com.stocklog.infra.kis.fundamental.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KisProfitRatioOutput {

    @JsonProperty("stac_yymm")
    private String closingDate;             // 결산 년월

    @JsonProperty("cptl_ntin_rate")
    private String returnOnTotalCapital; // 총자본 순이익률 (ROA와 유사한 지표)

    @JsonProperty("self_cptl_ntin_inrt")
    private String returnOnEquity;      // 자기자본 순이익률 (ROE)

    @JsonProperty("sale_ntin_rate")
    private String netProfitMargin;     // 매출액 순이익률 (Net Profit Margin)

    @JsonProperty("sale_totl_rate")
    private String grossProfitMargin;    // 매출액 총이익률 (Gross Profit Margin)
}