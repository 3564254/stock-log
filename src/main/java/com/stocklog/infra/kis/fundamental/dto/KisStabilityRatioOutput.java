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
public class KisStabilityRatioOutput {

    @JsonProperty("stac_yymm")
    private String closingDate;      // 결산 년월

    @JsonProperty("lblt_rate")
    private String debtRatio;     // 부채 비율 (%)

    @JsonProperty("bram_depn")
    private String debtDependency; // 차입금 의존도 (%)

    @JsonProperty("crnt_rate")
    private String currentRatio;  // 유동 비율 (%)

    @JsonProperty("quck_rate")
    private String quickRatio;   // 당좌 비율 (%)
}