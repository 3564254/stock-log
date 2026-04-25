package com.stocklog.infra.kis.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class KisResponseV1<T> {

    @JsonProperty("rt_cd")
    private String returnCode;

    @JsonProperty("msg1")
    private String message;

    @JsonProperty("output")
    private T output;

}
