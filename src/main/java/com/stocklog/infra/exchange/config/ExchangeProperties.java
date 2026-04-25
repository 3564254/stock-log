package com.stocklog.infra.exchange.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("exchange")
public record ExchangeProperties(
        String authKey
) {

}
