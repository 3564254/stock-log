package com.stocklog.infra.kis.price.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class KisDomesticPriceOutput {
    @JsonProperty("stck_prpr") private String currentPrice;
    @JsonProperty("prdy_vrss") private String changePrice;
    @JsonProperty("prdy_ctrt") private String changeRate;
    @JsonProperty("stck_sdpr") private String basePrice;
    @JsonProperty("per") private String per;
    @JsonProperty("pbr") private String pbr;
    @JsonProperty("eps") private String eps;
    @JsonProperty("bps") private String bps;
    @JsonProperty("hts_avls") private String marketCap;
    @JsonProperty("acml_vol") private String volume;
    @JsonProperty("w52_hgpr") private String w52High;
    @JsonProperty("w52_lwpr") private String w52Low;
}