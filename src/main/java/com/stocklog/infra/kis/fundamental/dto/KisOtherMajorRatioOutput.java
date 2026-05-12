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
public class KisOtherMajorRatioOutput {

    @JsonProperty("stac_yymm")
    private String closingDate;    // 결산 년월

    @JsonProperty("payout_rate")
    private String payoutRatio; // 배당 성향 (%)

    @JsonProperty("eva")
    private String eva;         // EVA (경제적 부가가치)

    @JsonProperty("ebitda")
    private String ebitda;      // EBITDA (법인세·이자·감가상각비 차감 전 영업이익)

    @JsonProperty("ev_ebitda")
    private String evEbitda;    // EV/EBITDA (기업가치 / EBITDA)
}