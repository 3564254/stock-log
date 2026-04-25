package com.stocklog.infra.finnhub.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "finnhub")
public class FinProperties {
    String apiKey;
}
