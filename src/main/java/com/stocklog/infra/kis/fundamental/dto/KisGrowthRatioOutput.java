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
public class KisGrowthRatioOutput {

    @JsonProperty("stac_yymm")
    private String closingDate;           // 결산 년월

    @JsonProperty("grs")
    private String revenueGrowthRate;  // 매출액 증가율 (%)

    @JsonProperty("bsop_prfi_inrt")
    private String operatingProfitGrowthRate; // 영업 이익 증가율 (%)

    @JsonProperty("equt_inrt")
    private String equityGrowthRate;   // 자기자본 증가율 (%)

    @JsonProperty("totl_aset_inrt")
    private String totalAssetGrowthRate; // 총자산 증가율 (%)
}