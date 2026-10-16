package com.stocklog.infra.kis.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;


@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class KisResponseV3<T> {
    @JsonProperty("rt_cd")
    private String returnCode;

    @JsonProperty("msg_cd")
    private String messageCode;

    @JsonProperty("msg1")
    private String message;

    @JsonProperty("outblock1")
    private T output;
}
