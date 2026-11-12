package com.stocklog.infra.kis.price.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/** 해외주식 현재체결가(HHDFS00000300) */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class KisDayPriceOutput {
    @JsonProperty("last") private String currentPrice;   // 현재가
    @JsonProperty("base") private String basePrice;      // 전일 종가
    @JsonProperty("tvol") private String volume;         // 거래량
}
