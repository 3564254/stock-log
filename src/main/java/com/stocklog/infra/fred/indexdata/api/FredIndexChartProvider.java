package com.stocklog.infra.fred.indexdata.api;

import com.stocklog.global.annotation.LogChartFetch;
import com.stocklog.modules.market.index.dto.IndexDto;
import com.stocklog.modules.market.index.dto.IndexChartDto;
import com.stocklog.modules.market.index.provider.IndexChartProvider;
import com.stocklog.modules.market.domain.type.IndexType;
import com.stocklog.infra.fred.config.FredProperties;
import com.stocklog.infra.fred.indexdata.dto.IndexChartOutput1;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class FredIndexChartProvider implements IndexChartProvider {

    private final WebClient webClient;
    private final FredProperties fredProperties;

    public FredIndexChartProvider(@Qualifier("fredWebClient") WebClient webClient, FredProperties fredProperties) {
        this.webClient = webClient;
        this.fredProperties = fredProperties;
    }

    @LogChartFetch
    @Override
    public IndexDto fetchIndexResponse(IndexType type) {
        try {
            IndexChartOutput1 output = callApi(type);

            if (output == null || output.getObservations() == null || output.getObservations().length == 0) {
                log.warn("FRED API 응답이 비어있습니다. type: {}", type);
                return IndexDto.createFallback();
            }

            List<IndexChartDto> chartDataList = Arrays.stream(output.getObservations())
                    .filter(obs -> obs.getValue() != null && !obs.getValue().equals("."))
                    .map(obs -> IndexChartDto.builder()
                            .date(LocalDate.parse(obs.getDate())) // (주의: 도메인 객체 필드명에 맞게 세팅. date가 아니라 time이면 .date(...) 으로 변경)
                            .close(new BigDecimal(obs.getValue()))
                            .build())
                    .collect(Collectors.toList());

                return IndexDto.builder()
                        .seriesId(type.getSeriesId())
                        .data(chartDataList)
                        .build();
            }
        catch (Exception e) {
        log.error("FRED API 통신 실패 [{}]: {}", type.getSeriesId(), e.getMessage());
        return IndexDto.createFallback();
    }}


    private IndexChartOutput1 callApi(IndexType type) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusYears(3);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/fred/series/observations")
                        .queryParam("series_id", type.getSeriesId())
                        .queryParam("api_key", fredProperties.getApiKey())
                        .queryParam("file_type", "json")
                        .queryParam("observation_start", start.toString())
                        .queryParam("observation_end", end.toString())
                        .build())
                .retrieve()
                .bodyToMono(IndexChartOutput1.class)
                .block();
    }
}
