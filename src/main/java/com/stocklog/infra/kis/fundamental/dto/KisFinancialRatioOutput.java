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
public class KisFinancialRatioOutput {

    @JsonProperty("stac_yymm")
    private String closingDate;          // 결산 년월

    @JsonProperty("grs")
    private String revenueGrowthRate; // 매출액 증가율 (YoY 등)

    @JsonProperty("bsop_prfi_inrt")
    private String operatingProfitGrowthRate; // 영업 이익 증가율

    @JsonProperty("ntin_inrt")
    private String netIncomeGrowthRate; // 순이익 증가율

    @JsonProperty("roe_val")
    private String roe;              // ROE 값 (Returns On Equity)

    @JsonProperty("eps")
    private String eps;              // EPS (주당순이익)

    @JsonProperty("sps")
    private String sps;              // SPS (주당매출액)

    @JsonProperty("bps")
    private String bps;              // BPS (주당순자산가치)

    @JsonProperty("rsrv_rate")
    private String reserveRatio;     // 유보 비율

    @JsonProperty("lblt_rate")
    private String debtRatio;        // 부채 비율
}