package com.stocklog.infra.fred.indexdata.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class IndexChartOutput1 {
    private String observation_start;
    private String observation_end;
    private IndexChartOutput2[] observations;
}
