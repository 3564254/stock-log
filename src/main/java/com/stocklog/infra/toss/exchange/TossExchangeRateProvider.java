package com.stocklog.infra.toss.exchange;

import com.stocklog.infra.toss.common.TossResponse;
import com.stocklog.infra.toss.config.auth.TossTokenManager;
import com.stocklog.modules.market.exchange.dto.ExchangeRateInfo;
import com.stocklog.modules.market.exchange.provider.ExchangeRateProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Component
@Order(1)
@Slf4j
public class TossExchangeRateProvider implements ExchangeRateProvider {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;
    private final TossTokenManager tossTokenManager;

    public TossExchangeRateProvider(@Qualifier("tossWebClient") WebClient webClient, TossTokenManager tossTokenManager) {
        this.webClient = webClient;
        this.tossTokenManager = tossTokenManager;
    }

    @Override
    public ExchangeRateInfo fetchExchangeRate() {
        TossExchangeRateOutput output = callWithTokenRetry();
        if (output == null || output.getRate() == null) return null;

        return ExchangeRateInfo.builder()
                .baseCurrency(output.getBaseCurrency())
                .quoteCurrency(output.getQuoteCurrency())
                .baseRate(toDecimal(output.getRate()))
                .midRate(toDecimal(output.getMidRate()))
                .basisPoint(toDecimal(output.getBasisPoint()))
                .rateChangeType(output.getRateChangeType())
                .validFrom(parseToLocalDateTime(output.getValidFrom()))
                .validUntil(parseToLocalDateTime(output.getValidUntil()))
                .build();
    }

    private TossExchangeRateOutput callWithTokenRetry() {
        try {
            return callApi();
        } catch (WebClientResponseException.Unauthorized e) {
            log.info("[Toss] 401 응답 → 토큰 재발급 후 재시도");
            tossTokenManager.invalidate();
            return callApi();
        }
    }

    private TossExchangeRateOutput callApi() {
        TossResponse<TossExchangeRateOutput> response = webClient.get()
                .uri(b -> b.path("/api/v1/exchange-rate")
                        .queryParam("baseCurrency", "USD")
                        .queryParam("quoteCurrency", "KRW")
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tossTokenManager.getAccessToken())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<TossResponse<TossExchangeRateOutput>>() {})
                .block(CALL_TIMEOUT);
        return response != null ? response.getResult() : null;
    }

    private static BigDecimal toDecimal(String s) {
        return (s == null || s.isBlank()) ? null : new BigDecimal(s);
    }

    private static LocalDateTime parseToLocalDateTime(String s) {
        return (s == null || s.isBlank()) ? null : OffsetDateTime.parse(s).toLocalDateTime();
    }
}
