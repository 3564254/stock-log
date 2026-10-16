package com.stocklog.infra.kis.news.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;

/**
 * 해외뉴스종합(제목) outblock1 한 건 — 필드명은 KIS 명세 그대로
 */
@Getter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class KisStockNewsOutput {

    @JsonProperty("info_gb")
    private String category;        // 뉴스구분

    @JsonProperty("news_key")
    private String newsId;          // 뉴스키

    @JsonProperty("data_dt")
    private String dataDate;        // 조회일자 yyyyMMdd

    @JsonProperty("data_tm")
    private String dataTime;        // 조회시간 HHmmss

    @JsonProperty("class_cd")
    private String classCode;       // 중분류

    @JsonProperty("class_name")
    private String className;       // 중분류명

    @JsonProperty("source")
    private String source;          // 자료원

    @JsonProperty("nation_cd")
    private String nationCode;      // 국가코드

    @JsonProperty("exchange_cd")
    private String exchangeCode;    // 거래소코드

    @JsonProperty("symb")
    private String symbol;          // 종목코드

    @JsonProperty("symb_name")
    private String symbolName;      // 종목명

    @JsonProperty("title")
    private String title;           // 제목 (헤드라인)
}
