package com.stocklog.infra.toss.exchange;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TossExchangeRateOutput {
    @JsonProperty("baseCurrency")
    private String baseCurrency;

    @JsonProperty("quoteCurrency")
    private String quoteCurrency;

    @JsonProperty("rate")
    private String rate;

    @JsonProperty("midRate")
    private String midRate;

    @JsonProperty("basisPoint")
    private String basisPoint;

    @JsonProperty("rateChangeType")
    private String rateChangeType;

    @JsonProperty("validFrom")
    private String validFrom;

    @JsonProperty("validUntil")
    private String validUntil;
}
