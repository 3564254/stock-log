package com.stocklog.infra.kis.chart.api;

import com.stocklog.domain.stock.dto.request.ChartRequest;
import com.stocklog.domain.stock.dto.response.StockChartResponse;
import com.stocklog.domain.stock.dto.vo.price.StockChartData;
import com.stocklog.domain.stock.provider.StockChartProvider;
import com.stocklog.domain.stock.type.MarketType;
import com.stocklog.domain.stock.type.Period;
import com.stocklog.infra.kis.chart.dto.KisOverseasChartOutput1;
import com.stocklog.infra.kis.chart.dto.KisOverseasChartOutput2;
import com.stocklog.infra.kis.common.KisResponseV2;
import com.stocklog.infra.kis.config.KisProperties;
import com.stocklog.infra.kis.config.client.KisTokenManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
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
        log.info("KIS 해외 fetchInfo Period: {}, Code: {}", period.name() ,request.code());
        try {
            List<KisOverseasChartOutput2> output = callApi(request,period);
            if (output == null) return StockChartResponse.createFallback();

            return StockChartResponse.builder()
                    .code(request.code())
                    .chartDataList(convertChart(output, period))
                    .build();
        } catch (Exception e) {
            log.info("해외 종목 정보 조회 중 오류: {}",e.getMessage());
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

        if (response != null) {
            KisOverseasChartOutput2 output2 =  response.getOutput2().get(0);
            log.info("time: {}, open: {}, value: {}, high:{}, low:{}, volume{}", output2.getTime(), output2.getOpen(), output2.getValue(), output2.getHigh(), output2.getLow(), output2.getVolume());
        }
        return (response != null && "0".equals(response.getReturnCode())) ? response.getOutput2() : null;
    }

    private List<StockChartData> convertChart(List<KisOverseasChartOutput2> list, Period period) {
        List<StockChartData> result = new ArrayList<>();

        for (KisOverseasChartOutput2 data : list) {
            if (data.getTime()  == null || data.getTime().isBlank())  continue;
            if (data.getValue() == null || data.getValue().isBlank()) continue;

            result.add(StockChartData.builder()
                    .time(StockChartData.formatDate(data.getTime()))
                    .value(new BigDecimal(data.getValue()))
                    .timeFrame(period)
                    .open(parseSafe(data.getOpen()))
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

        };
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
