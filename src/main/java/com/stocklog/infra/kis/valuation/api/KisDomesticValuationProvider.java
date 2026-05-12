package com.stocklog.infra.kis.valuation.api;

import com.stocklog.domain.stock.common.StockRequest;
import com.stocklog.domain.stock.common.type.MarketType;
import com.stocklog.domain.stock.valutation.StockValuationProvider;
import com.stocklog.domain.stock.valutation.dto.response.ValuationResponse;
import com.stocklog.infra.kis.config.KisProperties;
import com.stocklog.infra.kis.config.client.KisTokenManager;
import com.stocklog.infra.kis.valuation.dto.KisDomesticTargetPriceOutput;
import com.stocklog.infra.kis.valuation.dto.KisDomesticValuationOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static com.stocklog.global.util.FinancialUtils.safeParse;

@Component
@Slf4j
@RequiredArgsConstructor
public class KisDomesticValuationProvider implements StockValuationProvider {

    private final WebClient webClient;
    private final KisProperties properties;
    private final KisTokenManager tokenManager;

    @Override
    public ValuationResponse fetchValuation(StockRequest request) {
        log.info("[KIS-Valuation] 국내 주식 가치 평가 데이터 수집 시작 - 코드: {}", request.code());

        KisDomesticValuationOutput output = callApi(request.code());
        KisDomesticTargetPriceOutput targetPrice = getTargetPrice(request.code());

        if (output == null || output.getOutput3() == null || output.getOutput3().size() < 4) {
            log.warn("[KIS-Valuation] 응답 데이터 부족으로 Fallback 반환 - 코드: {}", request.code());
            return ValuationResponse.createFallback();
        }

        try {
            // 원본 데이터 로그 (디버깅용)
            log.info("[KIS-Valuation] Raw Data - Code: {}, F-EPS: {}, Growth: {}, F-PER: {}",
                    request.code(),
                    output.getOutput3().get(1).getData4(),
                    output.getOutput3().get(2).getData4(),
                    output.getOutput3().get(3).getData4());

            String fepsStr = output.getOutput3().get(1).getData4();
            String epsGrowthStr = output.getOutput3().get(2).getData4();
            String fperStr = output.getOutput3().get(3).getData4();

            BigDecimal feps = safeParse(fepsStr);
            BigDecimal fper = safeParse(fperStr).divide(BigDecimal.TEN, 2, RoundingMode.HALF_UP);
            BigDecimal epsGrowth = safeParse(epsGrowthStr).divide(BigDecimal.TEN, 2, RoundingMode.HALF_UP);

            BigDecimal peg = BigDecimal.ZERO;
            if (epsGrowth.compareTo(BigDecimal.ZERO) > 0) {
                peg = fper.divide(epsGrowth, 4, RoundingMode.HALF_UP);
            }

            BigDecimal pastEps = safeParse(output.getOutput3().get(1).getData1());
            BigDecimal target = BigDecimal.ZERO;

            if (targetPrice != null && targetPrice.getOutputs() != null && !targetPrice.getOutputs().isEmpty()) {
                target = getAvgPrice(targetPrice);
                log.info("[KIS-Valuation] 목표주가 계산 완료 (기관수: {}, 평균가: {})", targetPrice.getOutputs().size(), target);
            }

            log.info("[KIS-Valuation] 분석 성공 - 코드: {}, PEG: {}, Target: {}", request.code(), peg, target);

            return ValuationResponse.builder()
                    .feps(feps)
                    .fper(fper)
                    .peg(peg)
                    .eps(pastEps)
                    .targetPrice(target)
                    .build();

        } catch (Exception e) {
            log.error("[KIS-Valuation] 데이터 파싱 실패 - 코드: {}, 사유: {}", request.code(), e.getMessage());
            return ValuationResponse.createFallback();
        }
    }

    @Override
    public boolean supports(MarketType marketType) {
        return marketType.isDomestic();
    }

    private KisDomesticValuationOutput callApi(String code) {
        log.info("[KIS-Valuation] 가치평가 API 호출 - SHT_CD: {}", code);
        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/uapi/domestic-stock/v1/quotations/estimate-perform")
                            .queryParam("SHT_CD", code)
                            .build())
                    .headers(h -> {
                        h.add("authorization", "Bearer " + tokenManager.getAccessToken());
                        h.add("appkey", properties.appKey());
                        h.add("appsecret", properties.appSecret());
                        h.add("tr_id", "HHKST668300C0");
                        h.add("custtype","P");
                    })
                    .retrieve()
                    .bodyToMono(KisDomesticValuationOutput.class)
                    .block();
        } catch (Exception e) {
            log.error("[KIS-Valuation] 가치평가 API 호출 중 시스템 오류 - 코드: {}, 에러: {}", code, e.getMessage());
            return null;
        }
    }

    private KisDomesticTargetPriceOutput getTargetPrice(String code) {
        log.info("[KIS-Valuation] 목표주가 API 호출 - 코드: {}", code);
        LocalDate today = LocalDate.now();
        LocalDate past = today.minusMonths(3);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("'00'yyyyMMdd");

        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/uapi/domestic-stock/v1/quotations/invest-opinion")
                            .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                            .queryParam("FID_COND_SCR_DIV_CODE", "16633")
                            .queryParam("FID_INPUT_ISCD", code)
                            .queryParam("FID_INPUT_DATE_1", past.format(formatter))
                            .queryParam("FID_INPUT_DATE_2", today.format(formatter))
                            .build())
                    .headers(h -> {
                        h.add("authorization", "Bearer " + tokenManager.getAccessToken());
                        h.add("appkey", properties.appKey());
                        h.add("appsecret", properties.appSecret());
                        h.add("tr_id", "FHKST663300C0");
                        h.add("custtype", "P");
                    })
                    .retrieve()
                    .bodyToMono(KisDomesticTargetPriceOutput.class)
                    .block();
        } catch (Exception e) {
            log.error("[KIS-Valuation] 목표주가 API 호출 중 시스템 오류 - 코드: {}, 에러: {}", code, e.getMessage());
            return null;
        }
    }

    public BigDecimal getAvgPrice(KisDomesticTargetPriceOutput priceOutput) {
        if (priceOutput.getOutputs() == null || priceOutput.getOutputs().isEmpty()) return BigDecimal.ZERO;

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal size = BigDecimal.valueOf(priceOutput.getOutputs().size());
        for (KisDomesticTargetPriceOutput.TargetPrice targetPrice : priceOutput.getOutputs()) {
            total = total.add(new BigDecimal(targetPrice.getTargetPrice()));
        }
        return total.divide(size, 0, RoundingMode.HALF_UP);
    }
}