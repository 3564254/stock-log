package com.stocklog.infra.toss.config.auth;

import com.stocklog.infra.toss.config.TossProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/** 토스 OAuth2 Client Credentials 토큰 관리 */
@Component
@Slf4j
public class TossTokenManager {

    private static final Duration REFRESH_MARGIN = Duration.ofMinutes(2);
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final Duration ISSUE_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration FAILURE_COOLDOWN = Duration.ofMinutes(1);

    private final WebClient webClient;
    private final TossProperties properties;
    private final Clock clock = Clock.systemUTC();
    private final ReentrantLock lock = new ReentrantLock();

    private volatile String accessToken;
    private volatile Instant expiresAt = Instant.EPOCH;
    private volatile Instant retryAfter = Instant.EPOCH;   // 발급 실패 후 다시 시도할 수 있는 시각
    private volatile String lastFailure;

    public TossTokenManager(@Qualifier("tossWebClient") WebClient webClient, TossProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    public String getAccessToken() {
        if (isUsable()) return accessToken;

        lock.lock();
        try {
            if (isUsable()) return accessToken;   // 기다리는 동안 다른 스레드가 이미 발급했을 수 있다
            if (clock.instant().isBefore(retryAfter)) {
                throw new IllegalStateException("Toss 토큰 발급이 최근 실패해 잠시 쉬는 중: " + lastFailure);
            }
            try {
                issueAccessToken();
            } catch (RuntimeException e) {
                lastFailure = e.getMessage();
                retryAfter = clock.instant().plus(FAILURE_COOLDOWN);
                log.error("[Toss] 액세스 토큰 발급 실패 ({}초 뒤 재시도): {}", FAILURE_COOLDOWN.toSeconds(), lastFailure);
                throw e;
            }
            return accessToken;
        } finally {
            lock.unlock();
        }
    }

    /** 401 등으로 토큰이 무효해졌을 때 다음 호출에서 다시 발급하게 한다 */
    public void invalidate() {
        expiresAt = Instant.EPOCH;
    }

    private boolean isUsable() {
        return accessToken != null && clock.instant().isBefore(expiresAt.minus(REFRESH_MARGIN));
    }

    private void issueAccessToken() {
        if (isBlank(properties.getApiKey()) || isBlank(properties.getSecretKey())) {
            throw new IllegalStateException("toss.api-key / toss.secret-key 설정이 비어 있습니다.");
        }

        TossAccessTokenResponse response = webClient.post()
                .uri("/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "client_credentials")
                        .with("client_id", properties.getApiKey())
                        .with("client_secret", properties.getSecretKey()))
                .retrieve()
                // 실패하면 응답 본문(사유)을 메시지에 담는다
                .onStatus(HttpStatusCode::isError, res -> res.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> new IllegalStateException(
                                "Toss 토큰 발급 실패: HTTP " + res.statusCode().value() + " " + abbreviate(body))))
                .bodyToMono(TossAccessTokenResponse.class)
                .block(ISSUE_TIMEOUT);

        if (response == null || isBlank(response.getAccessToken())) {
            throw new IllegalStateException("Toss 토큰 발급 실패: 응답에 access_token이 없습니다.");
        }

        Duration ttl = response.getExpiresIn() != null && response.getExpiresIn() > 0
                ? Duration.ofSeconds(response.getExpiresIn())
                : DEFAULT_TTL;

        this.accessToken = response.getAccessToken();
        this.expiresAt = clock.instant().plus(ttl);
        log.info("[Toss] 액세스 토큰 발급 완료 (유효 {}초)", ttl.toSeconds());   // 토큰 값은 남기지 않는다
    }

    private static String abbreviate(String s) {
        String one = s.replaceAll("\\s+", " ").strip();
        return one.length() > 300 ? one.substring(0, 300) + "…" : one;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
