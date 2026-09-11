package com.stocklog.infra.toss.config;


import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
//@EnableConfigurationProperties(TossConfig.class)
@RequiredArgsConstructor
public class TossConfig {

    private final TossProperties tossProperties;

    @Bean
    @Qualifier("tossWebClient")
    public WebClient tossWebClient(TossProperties properties) {
        return WebClient.builder()
                .baseUrl("https://openapi.tossinvest.com")
                .build();

    }

}
