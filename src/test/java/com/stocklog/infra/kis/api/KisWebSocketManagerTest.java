package com.stocklog.infra.kis.api;

import com.stocklog.infra.kis.config.client.KisWebSocketManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
class KisWebSocketManagerTest {
    @Autowired
    private KisWebSocketManager kisWebSocketManager;

    @Test
    @DisplayName("KIS 실시간 (웹소켓) 접속키 발급")
    void getApprovalKey() {
        // when
        String key = kisWebSocketManager.getApprovalKey();

        System.out.println("발급된 키: " + key);

        assertThat(key).isNotNull();
        assertThat(key).isNotBlank();
    }
}