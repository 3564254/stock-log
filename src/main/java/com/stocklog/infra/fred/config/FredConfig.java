package com.stocklog.infra.fred.config;


import com.stocklog.infra.finnhub.config.FinProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(FredProperties.class)
@RequiredArgsConstructor
public class FredConfig {

    private final FredProperties fredProperties;

    @Bean
    @Qualifier("fredWebClient")
    public WebClient fredWebClient(FinProperties properties) {
        return WebClient.builder()
                .baseUrl("https://api.stlouisfed.org")
                // https://api.stlouisfed.org/fred/series/observations?series_id=GNPCA&api_key=abcdefghijklmnopqrstuvwxyz123456&file_type=json
                .build();
    }
}
