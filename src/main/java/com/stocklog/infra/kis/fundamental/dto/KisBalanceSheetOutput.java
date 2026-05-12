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
@AllArgsConstructor // Builder 사용을 위해 추가
@JsonIgnoreProperties(ignoreUnknown = true)
public class KisBalanceSheetOutput {

    @JsonProperty("stac_yymm")
    private String closingDate; // 결산 년월

    @JsonProperty("total_aset")
    private String totalAssets; // 자산총계

    @JsonProperty("total_lblt")
    private String totalLiabilities; // 부채총계

    @JsonProperty("total_cptl")
    private String stockholdersEquity; // 자본총계

    @JsonProperty("cras")
    private String currentAssets; // 유동자산

    @JsonProperty("flow_lblt")
    private String currentLiabilities; // 유동부채

    // --- 분석을 위해 추가하면 좋은 필드들 ---

    @JsonProperty("cpfn")
    private String capitalStock; // 자본금

    @JsonProperty("prfi_surp")
    private String retainedEarnings; // 이익잉여금

    @JsonProperty("fxas")
    private String fixedAssets; // 고정자산
}