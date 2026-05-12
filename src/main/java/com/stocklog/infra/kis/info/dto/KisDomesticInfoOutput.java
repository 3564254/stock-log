package com.stocklog.infra.kis.info.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class KisDomesticInfoOutput {
    // 기본 정보
    @JsonProperty("pdno") private String rawCode;            // 상품번호
    @JsonProperty("prdt_name") private String name;          // 상품명
    @JsonProperty("prdt_eng_name") private String nameEng;   // 상품영문명

    // 주식 및 시장 정보
    @JsonProperty("lstg_stqt") private String totalShares;   // 상장주수
    @JsonProperty("excg_dvsn_cd") private String marketCode; // 거래소구분코드
    @JsonProperty("papr") private String parValue;          // 액면가

    // 상태 및 위험 정보
    @JsonProperty("tr_stop_yn") private String isTradeStop;  // 거래정지여부 (Y/N)
    @JsonProperty("admn_item_yn") private String isAdminItem; // 관리종목여부 (Y/N)
    @JsonProperty("lstg_abol_dt") private String delistingDate; // 상장폐지일자 (값이 있으면 폐지 예정 혹은 완료)

    // ETF 관련 (해외 주식 DTO와 대응)
    @JsonProperty("etf_dvsn_cd") private String etfDivisionCode; // ETF구분코드 (0이면 해당없음)
    @JsonProperty("etf_chas_erng_rt_dbnb") private String leverage; // ETF추적수익율배수 (레버리지)

    public String getCode() {
        if (rawCode != null && rawCode.length() >= 6) {
            return rawCode.substring(rawCode.length() - 6);
        }
        return rawCode;
    }

    public String getIsEtf() {
        return (etfDivisionCode != null && !"0".equals(etfDivisionCode)) ? "Y" : "N";
    }

    public String getIsDelisted() {
        return (delistingDate != null && !delistingDate.trim().isEmpty()) ? "Y" : "N";
    }
}