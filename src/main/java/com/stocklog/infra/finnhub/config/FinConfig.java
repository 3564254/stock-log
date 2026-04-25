package com.stocklog.infra.finnhub.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(FinProperties.class)
@RequiredArgsConstructor
public class FinConfig {
    private FinProperties finProperties;

    @Bean
    @Qualifier("finnhubWebClient")
    public WebClient finnhubWebClient(FinProperties properties) {
        return WebClient.builder()
                .baseUrl("https://finnhub.io/api/v1") // 이 부분이 확실히 핀허브를 향해야 합니다.
                .defaultHeader("X-Finnhub-Token", properties.getApiKey())
                .build();
    }
}
