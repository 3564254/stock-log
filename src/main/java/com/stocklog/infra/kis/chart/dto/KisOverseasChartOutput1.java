package com.stocklog.infra.kis.chart.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class KisOverseasChartOutput1 {
    @JsonProperty("rsym")
    private String code;
}
