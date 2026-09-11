package com.stocklog.infra.toss.config.auth;

import com.stocklog.infra.toss.config.TossProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class TossTokenManager {
    private final WebClient webClient;
    private final TossProperties properties;

    private volatile String accessToken;

    public TossTokenManager(@Qualifier("tossWebClient") WebClient webClient, TossProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    public String getAccessToken() {
        if (accessToken == null) {
            accessToken = issueAccessToken();
            log.info("Toss Access Token 발급 완료: {}", accessToken);
        }
        return accessToken;
    }

    private String issueAccessToken() {
        Map<String, String> body = new HashMap<>();
        body.put("Content-Type", "application/x-www-form-urlencoded");
        body.put("grant_type", "client_credentials");
        body.put("client_id", properties.getApiKey());
        body.put("client_secret", properties.getSecretKey());

        TossAccessTokenResponse response = webClient.post()
                .uri("/oauth2/token")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(TossAccessTokenResponse.class)
                .block();

        if (response == null || response.getAccessToken() == null) {
            throw new RuntimeException("Toss 토큰 발급 실패");
        }
        return response.getAccessToken();
    }


}
