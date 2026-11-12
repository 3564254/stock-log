package com.stocklog.infra.kis.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.time.Duration;

/** KIS 호출 속도 제한 + 오류 본문 기록 */
@Slf4j
public class KisRequestThrottle implements ExchangeFilterFunction {

    private final long intervalNanos;
    private long nextFreeAt = 0L;   // 다음 요청을 보낼 수 있는 시각

    public KisRequestThrottle(int maxPerSecond) {
        this.intervalNanos = 1_000_000_000L / Math.max(1, maxPerSecond);
    }

    /** 한도 초과(EGW00201)일 때 재시도 횟수와 간격. 재시도로 성공하면 호출하는 쪽은 오류를 보지 않는다 */
    private static final int RATE_LIMIT_RETRIES = 2;
    private static final Duration RATE_LIMIT_BACKOFF = Duration.ofSeconds(1);
    private static final String RATE_LIMIT_CODE = "EGW00201";

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return attempt(request, next, 0);
    }

    private Mono<ClientResponse> attempt(ClientRequest request, ExchangeFunction next, int retry) {
        return Mono.defer(() -> {
            long waitNanos = reserveSlot();
            // 요청은 차례가 왔을 때(구독 시점) 만든다
            Mono<ClientResponse> call = Mono.defer(() -> next.exchange(request))
                    .flatMap(response -> handleServerError(request, next, response, retry));
            return waitNanos <= 0 ? call : Mono.delay(Duration.ofNanos(waitNanos)).then(call);
        });
    }

    /** 내 차례가 오기까지 기다릴 시간을 예약한다 */
    private synchronized long reserveSlot() {
        long now = System.nanoTime();
        long slot = Math.max(now, nextFreeAt);
        nextFreeAt = slot + intervalNanos;
        return slot - now;
    }

    /** 한도 초과를 받으면 줄 전체를 잠시 멈춘다 */
    private synchronized void coolDown(Duration d) {
        nextFreeAt = Math.max(nextFreeAt, System.nanoTime() + d.toNanos());
    }

    /** 5xx 처리 */
    private Mono<ClientResponse> handleServerError(ClientRequest request, ExchangeFunction next,
                                                   ClientResponse response, int retry) {
        if (!response.statusCode().is5xxServerError()) return Mono.just(response);
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> {
                    if (body.contains(RATE_LIMIT_CODE) && retry < RATE_LIMIT_RETRIES) {
                        log.warn("[KIS] 초당 호출 한도 초과 → {}초 쉬고 재시도 ({}/{}) {}",
                                RATE_LIMIT_BACKOFF.toSeconds(), retry + 1, RATE_LIMIT_RETRIES, request.url().getPath());
                        coolDown(RATE_LIMIT_BACKOFF);
                        return attempt(request, next, retry + 1);
                    }
                    log.warn("[KIS] {} {} → HTTP {} {}", request.method(), request.url().getPath(),
                            response.statusCode().value(), abbreviate(body));
                    return Mono.just(response.mutate().body(body).build());
                });
    }

    private static String abbreviate(String s) {
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() <= 300 ? one : one.substring(0, 300) + "…";
    }
}
