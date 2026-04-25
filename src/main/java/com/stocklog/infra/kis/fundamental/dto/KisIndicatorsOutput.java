package com.stocklog.infra.kis.fundamental.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KisIndicatorsOutput {

    @JsonProperty("eps")
    private String eps;              // 주당순이익 (EPS)

    @JsonProperty("roe_val")
    private String roe;              // 자기자본이익률 (ROE)

    @JsonProperty("lblt_rate")
    private String debtRatio;        // 부채비율 (%)

    @JsonProperty("crnt_rate")
    private String currentRatio;     // 유동비율 (%)

    @JsonProperty("grs")
    private String revenueGrowth;    // 매출액 증가율 (%)
}