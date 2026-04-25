package com.stocklog.infra.kis.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

@Getter
public class KisResponseV2<T,K> {
    @JsonProperty("rt_cd")
    private String returnCode;

    @JsonProperty("msg1")
    private String message;

    @JsonProperty("output1")
    private T output1;

    @JsonProperty("output2")
    private List<K> output2;
}
