package com.stocklog.infra.kis.fundamental.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KisBalanceSheetOutput {

    @JsonProperty("stac_yymm")
    private String closingDate;
    @JsonProperty("total_aset")
    private String assets; // 총자산 (자산총계)
    @JsonProperty("total_lblt") // 총부채 (부채총계)
    private String liabilities;
    @JsonProperty("total_cptl") // 자본총계 (주주지분)
    private String stockholdersEquity;
    @JsonProperty("cras") // 유동자산
    private String currentAssets;
    @JsonProperty("flow_lblt") // 유동부채
    private String currentLiabilities;

}
