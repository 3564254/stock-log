package com.stocklog.infra.kis.config;


import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import reactor.netty.resources.ConnectionProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(KisProperties.class)
@RequiredArgsConstructor
public class KisConfig {

    private final KisProperties kisProperties;

    @Bean
    @Primary
    public WebClient kisWebClient() {
        // 1. 커넥션 풀 설정
        // KIS 서버의 유휴 연결 끊기 시간보다 짧게 설정하는 것이 핵심입니다.
        ConnectionProvider provider = ConnectionProvider.builder("kis-api-pool")
                .maxConnections(50)                         // 최대 연결 수
                .maxIdleTime(Duration.ofSeconds(20))        // 유휴 상태 유지 시간 (20초 지나면 폐기)
                .maxLifeTime(Duration.ofSeconds(60))        // 연결 전체 수명
                .pendingAcquireTimeout(Duration.ofSeconds(60)) // 연결을 얻기 위해 대기하는 시간
                .evictInBackground(Duration.ofSeconds(20))  // 백그라운드에서 만료된 연결 제거 주기
                .build();

        // 2. HttpClient 설정 (타임아웃 등)
        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000) // 연결 타임아웃 5초
                .responseTimeout(Duration.ofSeconds(10))            // 응답 타임아웃 10초
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(10))  // 읽기 타임아웃
                                .addHandlerLast(new WriteTimeoutHandler(10)) // 쓰기 타임아웃
                );

        // 3. WebClient 빌드
        return WebClient.builder()
                .baseUrl(kisProperties.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

}
