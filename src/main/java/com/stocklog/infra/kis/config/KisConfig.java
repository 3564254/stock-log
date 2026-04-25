package com.stocklog.infra.kis.config;


import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(KisProperties.class)
@RequiredArgsConstructor
public class KisConfig {

    private final KisProperties kisProperties;

    @Bean
    @Primary
    public WebClient kisWebClient() {
        return WebClient.builder()
                .baseUrl(kisProperties.baseUrl())
                .build();
    }

}
