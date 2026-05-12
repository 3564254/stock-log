package com.stocklog.infra.kis.fundamental.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KisIncomeStatementOutput {
    @JsonProperty("sale_account")
    private String revenue;             // 매출액

    @JsonProperty("sale_totl_prfi")
    private String grossProfit;         // 매출총이익

    @JsonProperty("sell_mang")
    private String operatingExpenses;   // 판매 및 관리비 (영업비용으로 사용)

    @JsonProperty("bsop_prti")
    private String operatingIncome;     // 영업이익

    @JsonProperty("thtr_ntin")
    private String netIncomeLoss;       // 당기순이익
}
