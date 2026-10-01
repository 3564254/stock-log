package com.stocklog.infra.kis.chart.api;

import com.stocklog.modules.stock.chart.dto.ChartRequest;
import com.stocklog.modules.stock.chart.dto.StockChartDto;
import com.stocklog.modules.stock.chart.dto.StockChartResponse;
import com.stocklog.modules.stock.chart.StockChartProvider;
import com.stocklog.modules.stock.domain.type.MarketType;
import com.stocklog.modules.common.type.Period;
import com.stocklog.infra.kis.chart.dto.KisDomesticChartOutput1;
import com.stocklog.infra.kis.chart.dto.KisDomesticChartOutput2;
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

import static com.stocklog.modules.common.finance.util.FinancialUtils.safeParse;

@Component
@RequiredArgsConstructor
@Slf4j
public class KisDomesticChartProvider implements StockChartProvider {

    private final WebClient webClient;
    private final KisProperties properties;
    private final KisTokenManager tokenManager;

    @Override
    public StockChartResponse fetchInfo(ChartRequest request, Period period) {
        try {
            List<KisDomesticChartOutput2> output = callApi(request, period);
            if (output == null) return StockChartResponse.createFallback();

            return StockChartResponse.builder()
                    .code(request.code())
                    .chartDtoList(convertChart(output, period))
                    .build();
        } catch (Exception e) {
            log.error("국내 종목 차트 정보 조회 중 오류: {}", e.getMessage());
            return StockChartResponse.createFallback();
        }
    }

    @Override
    public boolean supports(MarketType marketType) {
        return marketType.isDomestic();
    }

    private List<KisDomesticChartOutput2> callApi(ChartRequest request, Period period) {
        Date date = new Date(period);

        KisResponseV2<KisDomesticChartOutput1, KisDomesticChartOutput2> response = webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")  // ← 파라미터명도 수정
                        .queryParam("FID_INPUT_ISCD", request.code())
                        .queryParam("FID_INPUT_DATE_1", date.startDate)
                        .queryParam("FID_INPUT_DATE_2", date.endDate)
                        .queryParam("FID_PERIOD_DIV_CODE", convertPeriod(period))
                        .queryParam("FID_ORG_ADJ_PRC", "1")
                        .build())
                .headers(h -> {
                    h.add("content-type", "application/json; charset=utf-8");
                    h.add("authorization", "Bearer " + tokenManager.getAccessToken());
                    h.add("appkey", properties.appKey());
                    h.add("appsecret", properties.appSecret());
                    h.add("tr_id", "FHKST03010100");
                    h.add("custtype", "P");
                })
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<KisResponseV2<KisDomesticChartOutput1, KisDomesticChartOutput2>>() {})
                .block();

        return (response != null && "0".equals(response.getReturnCode())) ? response.getOutput2() : null;
    }

    private List<StockChartDto> convertChart(List<KisDomesticChartOutput2> list, Period period) {
        List<StockChartDto> result = new ArrayList<>();

        for (KisDomesticChartOutput2 data : list) {
            if (data.getTime()  == null || data.getTime().isBlank())  continue;
            if (data.getValue() == null || data.getValue().isBlank()) continue;

            result.add(StockChartDto.builder()
                    .date(parsedDate(data.getTime()))
                    .close(new BigDecimal(data.getValue()))
                    .open(safeParse(data.getOpen()))
                    .high(safeParse(data.getHigh()))
                    .low(safeParse(data.getLow()))
                    .volume(safeParse(data.getVolume()))
                    .build());
        }
        return result;
    }

    private String convertPeriod(Period period) {
        return switch (period) {
            case DAY -> "D";
            case WEEK -> "W";
            case MONTH -> "M";
            default -> "";
        };
    }

    private static class Date {
        String startDate;
        String endDate;

        public Date(String startDate, String endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }

        public Date(Period period) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd");
            this.endDate = LocalDate.now().format(fmt);
            this.startDate = switch (period) {
                case DAY -> LocalDate.now().minusDays(100).format(fmt);
                case WEEK -> LocalDate.now().minusWeeks(100).format(fmt);
                case MONTH -> LocalDate.now().minusMonths(100).format(fmt);
                default -> LocalDate.now().minusWeeks(1).format(fmt);
            };
        }
    }

    private LocalDate parsedDate(String date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        return LocalDate.parse(date, formatter);
    }


}
