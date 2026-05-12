package com.stocklog.infra.alpha.valutaion.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(AlphaProperties.class)
@RequiredArgsConstructor
public class AlphaConfig {

    private AlphaProperties properties;

    @Bean
    @Qualifier("alphaWebClient")
    public WebClient alphaWebClient(AlphaProperties properties) {
        return WebClient.builder()
                .baseUrl("https://www.alphavantage.co") // 이 부분이 확실히 핀허브를 향해야 합니다.
                .defaultHeader("apiKey", properties.getApiKey())
                .build();
    }
}
