package com.stocklog.infra.alpha.valutaion.dto;

import lombok.Getter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlphaValuationOutput {

    // 기본 정보
    @JsonProperty("Symbol")
    private String symbol;

    @JsonProperty("Name")
    private String name;

    @JsonProperty("Currency")
    private String currency;

    @JsonProperty("Exchange")
    private String exchange;

    // Valuation 주요 지표 (ValuationResponse 매핑용)
    @JsonProperty("PEGRatio")
    private String pegRatio;          // peg 매핑용

    @JsonProperty("EPS")
    private String eps;               // eps 매핑용

    @JsonProperty("ForwardPE")
    private String forwardPe;         // fper 매핑용

    @JsonProperty("AnalystTargetPrice")
    private String targetPrice;       // targetPrice 매핑용

    // 기타 재무/보조 지표 (필요에 따라 사용)
    @JsonProperty("PERatio")
    private String peRatio;

    @JsonProperty("TrailingPE")
    private String trailingPe;

    @JsonProperty("BookValue")
    private String bookValue;

    @JsonProperty("PriceToBookRatio")
    private String priceToBookRatio;  // PBR

    @JsonProperty("DividendYield")
    private String dividendYield;

    @JsonProperty("DilutedEPSTTM")
    private String dilutedEpsTtm;

    @JsonProperty("Beta")
    private String beta;
}