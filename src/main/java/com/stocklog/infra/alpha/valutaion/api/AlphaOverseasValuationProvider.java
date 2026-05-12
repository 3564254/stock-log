package com.stocklog.infra.alpha.valutaion.api;

import com.stocklog.domain.stock.common.StockRequest;
import com.stocklog.domain.stock.common.type.MarketType;
import com.stocklog.domain.stock.valutation.StockValuationProvider;
import com.stocklog.domain.stock.valutation.dto.response.ValuationResponse;
import com.stocklog.infra.alpha.valutaion.config.AlphaProperties;
import com.stocklog.infra.alpha.valutaion.dto.AlphaValuationOutput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.stocklog.global.util.FinancialUtils.safeParse;

@Component
@Slf4j
public class AlphaOverseasValuationProvider implements StockValuationProvider {

    private final WebClient webClient;
    private final AlphaProperties alphaProperties;

    public AlphaOverseasValuationProvider(@Qualifier("alphaWebClient") WebClient webClient, AlphaProperties alphaProperties) {
        this.webClient = webClient;
        this.alphaProperties = alphaProperties;
    }

    @Override
    public ValuationResponse fetchValuation(StockRequest request) {
        AlphaValuationOutput output = callApi(request.code());

        if (output == null || output.getSymbol() == null) {
            log.warn("[AlphaOverseasValuationProvider] 데이터가 없거나 API 응답 실패. 코드: {}", request.code());
            return ValuationResponse.createFallback();
        }

        try {
            BigDecimal peg = safeParse(output.getPegRatio());
            BigDecimal eps = safeParse(output.getEps());
            BigDecimal fper = safeParse(output.getForwardPe());
            BigDecimal trailingPe = safeParse(output.getTrailingPe());
            BigDecimal targetPrice = safeParse(output.getTargetPrice());

            BigDecimal feps = BigDecimal.ZERO;
            if (fper.compareTo(BigDecimal.ZERO) > 0 && trailingPe.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal estimatedPrice = trailingPe.multiply(eps);
                feps = estimatedPrice.divide(fper, 2, RoundingMode.HALF_UP);
            }

            return ValuationResponse.builder()
                    .peg(peg)
                    .eps(eps)
                    .feps(feps)
                    .fper(fper)
                    .targetPrice(targetPrice)
                    .build();

        } catch (Exception e) {
            log.error("[AlphaOverseasValuationProvider] 가치 평가 데이터 파싱 중 오류 발생. 코드 {}: {}", request.code(), e.getMessage());
            return ValuationResponse.createFallback();
        }
    }

    @Override
    public boolean supports(MarketType marketType) {
        return marketType.isOverseas();
    }

    private AlphaValuationOutput callApi(String code) {
        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/query")
                            .queryParam("function", "OVERVIEW")
                            .queryParam("symbol", code)
                            .queryParam("apikey", alphaProperties.getApiKey())
                            .build())
                    .retrieve()
                    .bodyToMono(AlphaValuationOutput.class)
                    .block();
        } catch (Exception e) {
            log.error("[AlphaOverseasValuationProvider] API 호출 실패. 코드 {}: {}", code, e.getMessage());
            return null;
        }
    }
}