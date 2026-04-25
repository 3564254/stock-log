package com.stocklog.infra.kis.price.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class KisOverseasPriceOutput {
    @JsonProperty("last") private String currentPrice;    // 현재가
    @JsonProperty("base") private String basePrice;       // 전일종가
    @JsonProperty("tvol") private String volume;          // 거래량

    @JsonProperty("tomv") private String marketCap;       // 시가총액
    @JsonProperty("perx") private String per;             // PER
    @JsonProperty("pbrx") private String pbr;             // PBR
    @JsonProperty("epsx") private String eps;             // EPS
    @JsonProperty("bpsx") private String bps;             // BPS

    @JsonProperty("h52p") private String w52High;         // 52주 최고
    @JsonProperty("l52p") private String w52Low;          // 52주 최저

    @JsonProperty("t_xprc") private String wonPrice;      // 원화환산가
    @JsonProperty("t_rate") private String exchangeRate;  // 적용환율

    @JsonProperty("shar") private String totalShares;     // 상장주수 (보정용)


}
