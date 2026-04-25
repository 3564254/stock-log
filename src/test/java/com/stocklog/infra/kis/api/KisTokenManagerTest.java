package com.stocklog.infra.kis.api;

import com.stocklog.infra.kis.config.client.KisTokenManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;


@SpringBootTest
class KisTokenManagerTest {
    @Autowired
    private KisTokenManager kisTokenManager;

    @Test
    @DisplayName("KIS 접근 토큰 발급")
    void getAccessToken() {
        // when
        String token = kisTokenManager.getAccessToken();

        // then
        System.out.println("발급된 토큰: "+token);

        assertThat(token).isNotNull();
        assertThat(token).isNotBlank();
    }
}