package com.stocklog.infra.kis.info.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class KisOverseasInfoOutput {
    @JsonProperty("prdt_name") private String name;             // 상품명
    @JsonProperty("prdt_eng_name") private String nameEng;
    @JsonProperty("natn_cd") private String nationCode;         // 국가코드
    @JsonProperty("tr_mket_cd") private String marketCode;      // 시장코드
    @JsonProperty("tr_crcy_cd") private String currencyCode;    // 통화코드
    @JsonProperty("ovrs_stck_dvsn_cd") private String stockDivisionCode; // 주식구분코드

    @JsonProperty("lstg_stck_num") private String totalShares;  // 상장주수
    @JsonProperty("ovrs_papr") private String parValue;         // 액면가

    @JsonProperty("ovrs_stck_tr_stop_dvsn_cd") private String tradeStopCode; // 거래정지구분
    @JsonProperty("lstg_abol_item_yn") private String isDelisted;            // 상장폐지여부
    @JsonProperty("ptp_item_yn") private String isPtpItem;                  // PTP종목여부

    @JsonProperty("ovrs_stck_etf_risk_drtp_cd") private String isEtf;
    @JsonProperty("etp_chas_erng_rt_dbnb") private String leverage;


    @JsonProperty("mint_dcpt_trad_psbl_yn") private String isFractional;     // 소수점매매가능여부

    public String getIsTradeStop() {
        return "00".equals(tradeStopCode) ? "N" : "Y";
    }
}