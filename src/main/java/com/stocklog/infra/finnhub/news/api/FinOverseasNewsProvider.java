package com.stocklog.infra.finnhub.news.api;

import com.stocklog.modules.stock.application.request.StockRequest;
import com.stocklog.modules.stock.domain.type.MarketType;
import com.stocklog.modules.market.news.NewsProvider;
import com.stocklog.modules.market.news.NewsSource;
import com.stocklog.modules.market.news.dto.NewsDto;
import com.stocklog.infra.finnhub.config.FinProperties;
import com.stocklog.infra.finnhub.news.dto.FinStockNewsOutput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class FinOverseasNewsProvider implements NewsProvider {

    private final WebClient webClient;
    private final FinProperties finProperties;

    public FinOverseasNewsProvider(@Qualifier("finnhubWebClient") WebClient webClient, FinProperties finProperties) {
        this.webClient = webClient;
        this.finProperties = finProperties;
    }

    @Override
    public List<NewsDto> fetchStockNews(StockRequest request) {
        try {
            return processNewsOutputs(callStockNews(request));
        } catch (Exception e) {
            log.error("FIN 해외 News 정보 조회 중 오류: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<NewsDto> fetchMarketNews() {
        try {
            return processNewsOutputs(callMarketNews());
        } catch (Exception e) {
            log.error("FIN 해외 News 정보 조회 중 오류: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public boolean supports(MarketType marketType) {
        return marketType.isOverseas();
    }

    @Override
    public NewsSource source() {
        return NewsSource.FINNHUB;
    }

    private List<NewsDto> processNewsOutputs(List<FinStockNewsOutput> outputList) {
        if (outputList == null || outputList.isEmpty()) {
            return Collections.emptyList();
        }

        return outputList.stream()
                .filter(output -> {
                    if (output.getNewsId() == null) {
                        log.warn("[News] 핀허브 API 응답 중 id가 null인 기사 발견 (저장 스킵) - 제목: {}", output.getHeadLine());
                        return false;
                    }
                    return true;
                })
                .map(this::toNewsDto)
                .toList();
    }

    private List<FinStockNewsOutput> callStockNews(StockRequest request) {
        LocalDate today = LocalDate.now();
        LocalDate lastWeek = today.minusWeeks(1);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/company-news")
                        // 종목코드는 안전하게 대문자로 변환하여 요청
                        .queryParam("symbol", request.code().toUpperCase())
                        .queryParam("from", lastWeek)
                        .queryParam("to", today)
                        .build()
                )
                .header("X-Finnhub-Token", finProperties.getApiKey())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<FinStockNewsOutput>>() {})
                .block();
    }

    private List<FinStockNewsOutput> callMarketNews() {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/news")
                        // 종목코드는 안전하게 대문자로 변환하여 요청
                        .queryParam("category", "general")
                        .build()
                )
                .header("X-Finnhub-Token", finProperties.getApiKey())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<FinStockNewsOutput>>() {})
                .block();
    }




    // ✨ 단건 FinStockNewsOutput을 StockNewsDto로 매핑하는 내부 헬퍼 메서드
    private NewsDto toNewsDto(FinStockNewsOutput output) {
        return NewsDto.builder()
                .category(output.getCategory())
                .dateTime(convertToKst(output.getUnixDateTime()))
                .headline(output.getHeadLine())
                .newsId(output.getNewsId() != null ? String.valueOf(output.getNewsId()) : null)
                .image(output.getImage())
                .related(output.getRelated())
                .source(output.getSource())
                .summary(output.getSummary())
                .url(output.getUrl())
                .build();
    }


    // 뉴스 시각은 출처와 상관없이 KST로 저장한다 (KIS 뉴스와 같은 기준으로 정렬되도록)
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private LocalDateTime convertToKst(Long unixSeconds) {
        if (unixSeconds == null) {
            return LocalDateTime.now(KST);
        }

        try {
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(unixSeconds), KST);
        } catch (Exception e) {
            log.warn("시간 변환 중 오류 발생 (입력값: {}). 현재 시간으로 대체합니다.", unixSeconds);
            return LocalDateTime.now(KST);
        }
    }
}