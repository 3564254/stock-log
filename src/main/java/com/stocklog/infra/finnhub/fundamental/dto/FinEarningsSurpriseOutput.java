package com.stocklog.infra.finnhub.fundamental.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FinEarningsSurpriseOutput {

    private String symbol;
    private int year;
    private int quarter;
    private String period;

    private BigDecimal actual;
    private BigDecimal estimate;
    private BigDecimal surprise;
    private BigDecimal surprisePercent;

}
