package com.stocklog.infra.kis.news.api;

import com.stocklog.infra.kis.common.KisResponseV3;
import com.stocklog.infra.kis.config.KisProperties;
import com.stocklog.infra.kis.config.client.KisTokenManager;
import com.stocklog.infra.kis.news.dto.KisStockNewsOutput;
import com.stocklog.modules.market.news.NewsProvider;
import com.stocklog.modules.market.news.NewsSource;
import com.stocklog.modules.market.news.dto.NewsDto;
import com.stocklog.modules.stock.application.request.StockRequest;
import com.stocklog.modules.stock.domain.type.MarketType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * KIS 해외뉴스종합(제목) — GET /uapi/overseas-price/v1/quotations/news-title, TR HHPSTH60100C1
 * 실전 도메인 전용 (모의투자 미지원)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KisOverseasNewsProvider implements NewsProvider {

    private static final String PATH = "/uapi/overseas-price/v1/quotations/news-title";
    private static final String TR_ID = "HHPSTH60100C1";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");   // KIS가 주는 시각은 KST

    private final WebClient webClient;
    private final KisProperties properties;
    private final KisTokenManager tokenManager;

    @Override
    public List<NewsDto> fetchStockNews(StockRequest stockRequest) {
        List<NewsDto> news = processNewsOutputs(stockRequest.code());
        log.info("[News] KIS 종목 뉴스 {}건 - {}", news.size(), stockRequest.code());
        return news;
    }

    @Override
    public List<NewsDto> fetchMarketNews() {
        List<NewsDto> news = processNewsOutputs("");
        log.info("[News] KIS 시장 뉴스 {}건", news.size());
        return news;
    }

    @Override
    public boolean supports(MarketType marketType) {
        return marketType.isOverseas();
    }

    @Override
    public NewsSource source() {
        return NewsSource.KIS;
    }

    private List<NewsDto> processNewsOutputs(String code) {
        return callApi(code == null ? "" : code).stream()
                .filter(output -> {
                    if (output.getNewsId() == null || output.getTitle() == null || output.getTitle().isBlank()) {
                        log.warn("[News] KIS 응답 중 id 또는 제목이 없는 기사 스킵 - {}", output);
                        return false;
                    }
                    return true;
                })
                .map(this::toNewsDto)
                .toList();
    }

    private List<KisStockNewsOutput> callApi(String code) {
        try {
            KisResponseV3<List<KisStockNewsOutput>> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder.path(PATH)
                            .queryParam("INFO_GB", "")
                            .queryParam("CLASS_CD", "")
                            .queryParam("NATION_CD", "US")
                            .queryParam("EXCHANGE_CD", "")
                            .queryParam("SYMB", code)
                            .queryParam("DATA_DT", "")
                            .queryParam("DATA_TM", "")
                            .queryParam("CTS", "")
                            .build())
                    .headers(h -> {
                        h.add("content-type", "application/json; charset=utf-8");
                        h.add("authorization", "Bearer " + tokenManager.getAccessToken());
                        h.add("appkey", properties.appKey());
                        h.add("appsecret", properties.appSecret());
                        h.add("tr_id", TR_ID);
                        h.add("custtype", "P");
                    })
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<KisResponseV3<List<KisStockNewsOutput>>>() {})
                    .block();

            if (response == null) {
                log.warn("[News] KIS 뉴스 응답이 비어 있음 - symb={}", code);
                return List.of();
            }
            if (!"0".equals(response.getReturnCode())) {
                // 모의투자 도메인, 파라미터 누락, 호출 한도 초과 등은 여기로 온다
                log.warn("[News] KIS 뉴스 실패 - symb={}, rt_cd={}, msg_cd={}, msg={}",
                        code, response.getReturnCode(), response.getMessageCode(), response.getMessage());
                return List.of();
            }
            return response.getOutput() != null ? response.getOutput() : List.of();

        } catch (WebClientResponseException e) {
            log.warn("[News] KIS 뉴스 HTTP {} - symb={}, body={}", e.getStatusCode(), code, e.getResponseBodyAsString());
            return List.of();
        } catch (Exception e) {
            log.warn("[News] KIS 뉴스 호출 오류 - symb={}, {}", code, e.toString());
            return List.of();
        }
    }

    private NewsDto toNewsDto(KisStockNewsOutput output) {
        String category = (output.getClassName() != null && !output.getClassName().isBlank())
                ? output.getClassName() : output.getCategory();
        return NewsDto.builder()
                .category(category)
                .dateTime(convertLocalDateTime(output.getDataDate(), output.getDataTime()))
                .headline(output.getTitle())
                .newsId(output.getNewsId())
                .related(output.getSymbol() != null ? output.getSymbol() : "")
                .source(output.getSource())
                .summary(output.getTitle())   // 제목 API라 본문 요약이 없다
                .url("")
                .build();
    }

    private LocalDateTime convertLocalDateTime(String date, String time) {
        try {
            String t = time == null ? "" : time.trim();
            while (t.length() < 6) t = "0" + t;          // 93000 -> 093000
            return LocalDateTime.parse(date.trim() + t, DATE_TIME);
        } catch (DateTimeParseException | NullPointerException e) {
            log.warn("[News] 시간 변환 실패 (입력값: {} {}). 현재 시간으로 대체", date, time);
            return LocalDateTime.now();
        }
    }
}
