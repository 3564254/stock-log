package com.stocklog.infra.kis.valuation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class KisDomesticTargetPriceOutput {

    @JsonProperty("rt_cd")
    private String rtCd;

    @JsonProperty("msg_cd")
    private String msgCd;

    @JsonProperty("msg1")
    private String msg1;

    @JsonProperty("output")
    private List<TargetPrice> outputs;

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TargetPrice {
        @JsonProperty("hts_goal_prc")
        private String targetPrice;
    }
}
