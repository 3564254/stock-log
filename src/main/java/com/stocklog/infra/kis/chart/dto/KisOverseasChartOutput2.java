package com.stocklog.infra.kis.chart.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class KisOverseasChartOutput2 {
    @JsonProperty("xymd")
    private String time;
    @JsonProperty("open")
    private String open;
    @JsonProperty("clos")
    private String value;
    @JsonProperty("high")
    private String high;
    @JsonProperty("low")
    private String low;
    @JsonProperty("tvol")
    private String volume;
}
