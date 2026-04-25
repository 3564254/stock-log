package com.stocklog.infra.exchange.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ExchangeProperties.class)
@RequiredArgsConstructor
public class ExchangeConfig {
    private ExchangeProperties exchangeProperties;
}
