package com.stocklog.infra.kis.chart.api;

import com.stocklog.modules.stock.chart.dto.ChartRequest;
import com.stocklog.modules.stock.chart.dto.StockChartDto;
import com.stocklog.modules.stock.chart.dto.StockChartResponse;
import com.stocklog.modules.stock.chart.StockChartProvider;
import com.stocklog.modules.stock.domain.type.MarketType;
import com.stocklog.modules.stock.domain.type.Period;
import com.stocklog.infra.kis.chart.dto.KisOverseasChartOutput1;
import com.stocklog.infra.kis.chart.dto.KisOverseasChartOutput2;
import com.stocklog.infra.kis.common.KisResponseV2;
import com.stocklog.infra.kis.config.KisProperties;
import com.stocklog.infra.kis.config.client.KisTokenManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;



@Component
@Slf4j
@RequiredArgsConstructor
public class KisOverseasChartProvider implements StockChartProvider {

    private final WebClient webClient;
    private final KisProperties properties;
    private final KisTokenManager tokenManager;


    @Override
    public StockChartResponse fetchInfo(ChartRequest request, Period period) {
        try {
            List<KisOverseasChartOutput2> output = callApi(request,period);
            if (output == null) return StockChartResponse.createFallback();

            return StockChartResponse.builder()
                    .code(request.code())
                    .chartDtoList(convertChart(output, period))
                    .build();
        } catch (Exception e) {
            log.error("해외 종목 정보 조회 중 오류: {}",e.getMessage());
            return StockChartResponse.createFallback();
        }
    }

    @Override
    public boolean supports(MarketType marketType) {
        return marketType.isOverseas();
    }

    private List<KisOverseasChartOutput2> callApi(ChartRequest chartRequest, Period period) {
        KisResponseV2<KisOverseasChartOutput1, KisOverseasChartOutput2> response = webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/uapi/overseas-price/v1/quotations/dailyprice")
                        .queryParam("AUTH", "")
                        .queryParam("EXCD", chartRequest.marketType().getExchangeCode())
                        .queryParam("SYMB", chartRequest.code())
                        .queryParam("GUBN", convertPeriod(period))
                        .queryParam("BYMD", chartRequest.localDate())
                        .queryParam("MODP", "1")
                        .build())
                .headers(h -> {
                    h.add("content-type", "application/json; charset=utf-8");
                    h.add("authorization", "Bearer " + tokenManager.getAccessToken());
                    h.add("appkey", properties.appKey());
                    h.add("appsecret", properties.appSecret());
                    h.add("tr_id", "HHDFS76240000");
                })
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<KisResponseV2<KisOverseasChartOutput1, KisOverseasChartOutput2>>() {
                })
                .block();

        return (response != null && "0".equals(response.getReturnCode())) ? response.getOutput2() : null;
    }

    private List<StockChartDto> convertChart(List<KisOverseasChartOutput2> list, Period period) {
        List<StockChartDto> result = new ArrayList<>();

        for (KisOverseasChartOutput2 data : list) {
            if (data.getTime()  == null || data.getTime().isBlank())  continue;
            if (data.getClose() == null || data.getClose().isBlank()) continue;

            result.add(StockChartDto.builder()
                    .time(parsedDate(data.getTime()))
                    .open(parseSafe(data.getOpen()))
                    .close(parseSafe(data.getClose()))
                    .high(parseSafe(data.getHigh()))
                    .low(parseSafe(data.getLow()))
                    .volume(parseSafe(data.getVolume()))
                    .build());
        }
        return result;
    }

    private String convertPeriod(Period period) {
        return switch (period) {
            case DAY -> "0";
            case WEEK -> "1";
            case MONTH -> "2";
            default -> "";
        };
    }

    private LocalDate parsedDate(String date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        return LocalDate.parse(date, formatter);
    }

    private BigDecimal parseSafe(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
