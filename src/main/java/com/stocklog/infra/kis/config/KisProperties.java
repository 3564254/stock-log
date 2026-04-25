package com.stocklog.infra.kis.config;

import com.stocklog.domain.stock.type.Nation;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kis")
public record KisProperties (
    String appKey,
    String appSecret,
    String baseUrl
) {
}
