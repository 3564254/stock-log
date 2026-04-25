package com.stocklog.infra.fred.indexdata.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class IndexChartOutput2 {
    private String date;
    private String value;
}


