package com.stocklog.infra.kis.config.client;


import com.stocklog.infra.kis.config.KisProperties;
import com.stocklog.infra.kis.config.auth.KisWebSocketKeyResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class KisWebSocketManager {
    private final WebClient webClient;
    private final KisProperties kisProperties;
    private String approvalKey;


    public String getApprovalKey() {
        if (approvalKey == null) {
            approvalKey = issueApprovalKey();
            log.info("KIS 실시간 (웹소켓) 접속키 발급: {}", approvalKey);
        }
        return approvalKey;
    }


    private String issueApprovalKey() {
        Map<String, String> body = new HashMap<>();
        body.put("grant_type", "client_credentials");
        body.put("appkey", kisProperties.appKey());
        body.put("secretkey", kisProperties.appSecret());

        KisWebSocketKeyResponse response = webClient.post()
                .uri("/oauth2/Approval")
                .header("Content-Type", "application/json; charset=utf-8")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(KisWebSocketKeyResponse.class)
                .block();

        if (response == null || response.getApprovalKey() == null) {
            throw new RuntimeException("KIS 토큰 발급 실패");
        }

        return response.getApprovalKey();
    }


}
