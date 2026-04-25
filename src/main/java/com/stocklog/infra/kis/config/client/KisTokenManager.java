package com.stocklog.infra.kis.config.client;

import com.stocklog.infra.kis.config.KisProperties;
import com.stocklog.infra.kis.config.auth.KisAccessTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class KisTokenManager {
    private final WebClient webClient;
    private final KisProperties kisProperties;

    private volatile String accessToken;


    public String getAccessToken() {
        if (accessToken == null) {
            accessToken = issueAccessToken();
            log.info("KIS Access Token 발급 완료: {}", accessToken);
        }
        return accessToken;
    }


    private String issueAccessToken() {
        Map<String, String> body = new HashMap<>();
        body.put("grant_type", "client_credentials");
        body.put("appkey", kisProperties.appKey());
        body.put("appsecret", kisProperties.appSecret());

        KisAccessTokenResponse response = webClient.post()
                .uri("/oauth2/tokenP")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(KisAccessTokenResponse.class)
                .block();

        if (response == null || response.getAccessToken() == null) {
            throw new RuntimeException("KIS 토큰 발급 실패");
        }

        return response.getAccessToken();

    }
}
