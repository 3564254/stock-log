package com.stocklog.infra.toss.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(TossProperties.class)
public class TossConfig {

    public static final String BASE_URL = "https://openapi.tossinvest.com";

    @Bean
    public WebClient tossWebClient() {
        // 토스는 API 그룹별 초당 호출 한도가 있어 연결을 많이 열 필요가 없다
        ConnectionProvider provider = ConnectionProvider.builder("toss-pool")
                .maxConnections(50)
                .maxIdleTime(Duration.ofSeconds(20))
                .maxLifeTime(Duration.ofSeconds(60))
                .pendingAcquireTimeout(Duration.ofSeconds(10))
                .evictInBackground(Duration.ofSeconds(120))
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                // 토스는 응답(특히 에러 본문)을 gzip으로 보낸다 — 켜 두면 Netty가 풀어서 넘겨준다
                .compress(true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(10))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(10))
                        .addHandlerLast(new WriteTimeoutHandler(10)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(BASE_URL)
                .build();
    }
}
