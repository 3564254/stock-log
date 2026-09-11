package com.stocklog.infra.toss.exchange;

import com.stocklog.modules.market.exchange.dto.ExchangeRateInfo;
import com.stocklog.modules.market.exchange.provider.ExchangeRateProvider;
import com.stocklog.infra.toss.common.TossResponse;
import com.stocklog.infra.toss.config.TossProperties;
import com.stocklog.infra.toss.config.auth.TossTokenManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

//@Component
@Slf4j
public class TossExchangeRateProvider implements ExchangeRateProvider {

    private final WebClient webClient;
    private final TossProperties tossProperties;
    private final TossTokenManager tossTokenManager;

    public TossExchangeRateProvider(@Qualifier("tossWebClient") WebClient webClient, TossProperties tossProperties, TossTokenManager tossTokenManager) {
        this.webClient = webClient;
        this.tossProperties = tossProperties;
        this.tossTokenManager = tossTokenManager;
    }

    @Override
    public ExchangeRateInfo fetchExchangeRate() {
        try {
            TossExchangeRateOutput output = callApi();
            if (output == null) ExchangeRateInfo.createFallback();

            return ExchangeRateInfo.builder()
                    .baseCurrency(output.getBaseCurrency())
                    .quoteCurrency(output.getQuoteCurrency())
                    .baseRate(new BigDecimal(output.getRate()))
                    .minRate(new BigDecimal(output.getMidRate()))
                    .basisPoint(new BigDecimal(output.getBasisPoint()))
                    .rateChangeType(output.getRateChangeType())
                    .validFrom(parseToLocalDateTime(output.getValidFrom()))
                    .validUntil(parseToLocalDateTime(output.getValidUntil()))
                    .build();

        } catch (Exception e) {
            log.info("Toss 환율 조회 중 오류 발생", e.getMessage());
            return ExchangeRateInfo.createFallback();
        }

    }

    private TossExchangeRateOutput callApi() {
        TossResponse<TossExchangeRateOutput> response = webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/exchange-rate?baseCurrency=USD&quoteCurrency=KRW")
                        .build())
                .header("Authorization", "Bearer " + tossTokenManager.getAccessToken())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<TossResponse<TossExchangeRateOutput>>() {
                })
                .block();
        return (response != null ? response.getResult() : null);
    }

    private LocalDateTime parseToLocalDateTime(String dateTimeStr) {
        return OffsetDateTime.parse(dateTimeStr).toLocalDateTime();
    }
}
