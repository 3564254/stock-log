package com.stocklog.infra.kis.config.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class KisWebSocketKeyResponse {
    @JsonProperty("approval_key")
    private String approvalKey;
}
