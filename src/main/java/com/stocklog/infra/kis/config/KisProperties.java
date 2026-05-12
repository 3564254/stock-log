package com.stocklog.infra.kis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kis")
public record KisProperties (
    String appKey,
    String appSecret,
    String baseUrl
) {
}
