package com.stocklog.infra.kis.info.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class KisDomesticInfoOutput {
    @JsonProperty("pdno")
    private String rawCode;
    @JsonProperty("prdt_name")
    private String name;
    @JsonProperty("std_idst_clsf_cd_name")
    private String sector;
    @JsonProperty("idx_bztp_lcls_cd_name")
    private String indexLarge;
    @JsonProperty("idx_bztp_mcls_cd_name")
    private String indexMedium;
    @JsonProperty("idx_bztp_scls_cd_name")
    private String indexSmall;
    @JsonProperty("lstg_stqt")
    private String totalShares;
    @JsonProperty("excg_dvsn_cd")
    private String marketCode;
    @JsonProperty("tr_stop_yn")
    private String isTradeStop;
    @JsonProperty("admn_item_yn")
    private String isAdminItem;
    @JsonProperty("papr")
    private String parValue;

    public String getCode() {
        return (rawCode != null && rawCode.length() >= 6) ? rawCode.substring(rawCode.length() - 6) : rawCode;
    }
}