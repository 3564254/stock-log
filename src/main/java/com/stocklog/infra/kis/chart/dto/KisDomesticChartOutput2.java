package com.stocklog.infra.kis.chart.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class KisDomesticChartOutput2 {
    @JsonProperty("stck_bsop_date")  // ← xymd → stck_bsop_date
    private String time;
    @JsonProperty("stck_oprc")
    private String open;
    @JsonProperty("stck_clpr")
    private String value;
    @JsonProperty("stck_hgpr")
    private String high;
    @JsonProperty("stck_lwpr")
    private String low;
    @JsonProperty("acml_vol")
    private String volume;
}
