package com.stocklog.infra.kis.valuation.dto;

import lombok.Getter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class KisDomesticValuationOutput {

    @JsonProperty("rt_cd")
    private String rtCd;

    @JsonProperty("msg_cd")
    private String msgCd;

    @JsonProperty("msg1")
    private String msg1;

    @JsonProperty("output1")
    private Output1 output1;

    @JsonProperty("output2")
    private List<OutputData> output2;

    @JsonProperty("output3")
    private List<OutputData> output3;

    @JsonProperty("output4")
    private List<Output4> output4;

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Output1 {
        @JsonProperty("sht_cd") private String shtCd;
        @JsonProperty("item_kor_nm") private String itemKorNm;
        @JsonProperty("name1") private String name1;
        @JsonProperty("name2") private String name2;
        @JsonProperty("estdate") private String estdate;
        @JsonProperty("rcmd_name") private String rcmdName;
        @JsonProperty("capital") private String capital;
        @JsonProperty("forn_item_lmtrt") private String fornItemLmtrt;
    }

    // output2와 output3 공통 사용
    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OutputData {
        @JsonProperty("data1") private String data1;
        @JsonProperty("data2") private String data2;
        @JsonProperty("data3") private String data3;
        @JsonProperty("data4") private String data4;
        @JsonProperty("data5") private String data5;
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Output4 {
        @JsonProperty("dt") private String dt;
    }
}