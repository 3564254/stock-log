package com.stocklog.infra.toss.market.ranking;

import com.stocklog.infra.toss.common.TossResponse;
import com.stocklog.infra.toss.config.auth.TossTokenManager;
import com.stocklog.infra.toss.market.ranking.TossMarketRankingOutput.Ranking;
import com.stocklog.modules.common.type.Nation;
import com.stocklog.modules.market.ranking.MarketRankingInfo;
import com.stocklog.modules.market.ranking.MarketRankingInfo.MarketRanking;
import com.stocklog.modules.market.ranking.MarketRankingProvider;
import com.stocklog.modules.stock.domain.type.Currency;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * 토스 거래대금 순위 — GET /api/v1/rankings
 */
@Component
@Slf4j
public class TossMarketRankingProvider implements MarketRankingProvider {

    private static final String PATH = "/api/v1/rankings";               // baseUrl은 TossConfig에서 지정
    private static final String RANKING_TYPE = "MARKET_TRADING_AMOUNT";   // 시장 전체 거래대금 상위
    private static final String REALTIME = "realtime";                    // 장중 실시간
    private static final String DAILY = "1d";                             // 장이 닫혀 있으면 실시간 순위가 비어 온다 → 직전 거래일
    private static final int COUNT = 20;                                  // 화면엔 상위 몇 개만 쓴다 (API 최대 100)

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;
    private final TossTokenManager tossTokenManager;

    public TossMarketRankingProvider(@Qualifier("tossWebClient") WebClient webClient, TossTokenManager tossTokenManager) {
        this.webClient = webClient;
        this.tossTokenManager = tossTokenManager;
    }

    /**
     * 국가별 거래대금 순위. 실패하면 빈 결과(rankedAt = null)를 돌려준다
     * — 화면 한 구역 때문에 페이지 전체가 깨지지 않도록
     */
    @Override
    public MarketRankingInfo fetchMarketRankings(Nation nation) {
        // 실시간 순위가 없으면(장 마감·집계 전) 1일 순위로 대체. changeRate·basePrice는 둘 다 전일 대비
        MarketRankingInfo info = fetch(nation, REALTIME);
        if (info.rankings().isEmpty()) {
            log.debug("[Ranking] 토스 {} 실시간 순위 없음 → 1일 순위로 대체", nation);
            info = fetch(nation, DAILY);
        }
        return info;
    }

    private MarketRankingInfo fetch(Nation nation, String duration) {
        try {
            TossMarketRankingOutput output = callApi(nation, duration);
            if (output == null || output.rankings() == null || output.rankings().isEmpty()) {
                if (DAILY.equals(duration)) log.warn("[Ranking] 토스 {} 순위 응답이 비어 있음 ({})", nation, duration);
                return empty(nation);
            }

            List<MarketRanking> rankings = output.rankings().stream()
                    .map(this::toRanking)
                    .filter(Objects::nonNull)
                    .toList();

            return MarketRankingInfo.builder()
                    .nation(nation)
                    .realtime(REALTIME.equals(duration))
                    .rankedAt(output.rankedAt() != null ? toKst(output.rankedAt()) : LocalDateTime.now(KST))
                    .rankings(rankings)
                    .build();

        } catch (WebClientResponseException e) {
            log.warn("[Ranking] 토스 {} 순위({}) HTTP {} - {}", nation, duration, e.getStatusCode(), e.getResponseBodyAsString());
            return empty(nation);
        } catch (Exception e) {
            log.warn("[Ranking] 토스 {} 순위({}) 조회 실패: {}", nation, duration, e.toString());
            return empty(nation);
        }
    }

    private static MarketRankingInfo empty(Nation nation) {
        return MarketRankingInfo.builder()
                .nation(nation)
                .rankedAt(null)
                .rankings(List.of())
                .build();
    }

    private TossMarketRankingOutput callApi(Nation nation, String duration) {
        TossResponse<TossMarketRankingOutput> response = webClient.get()
                .uri(uriBuilder -> uriBuilder.path(PATH)
                        .queryParam("type", RANKING_TYPE)
                        .queryParam("marketCountry", nation.name())
                        .queryParam("duration", duration)
                        .queryParam("count", COUNT)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tossTokenManager.getAccessToken())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<TossResponse<TossMarketRankingOutput>>() {})
                .block(CALL_TIMEOUT);
        return response != null ? response.getResult() : null;
    }

    /** 종목 하나 변환. 종목코드나 현재가가 없으면 순위에서 뺀다 */
    private MarketRanking toRanking(Ranking r) {
        if (r == null || r.symbol() == null || r.symbol().isBlank()) return null;

        BigDecimal lastPrice = r.price() != null ? toDecimal(r.price().lastPrice()) : null;
        if (lastPrice == null) {
            log.debug("[Ranking] 현재가 없는 종목 스킵 - {}", r.symbol());
            return null;
        }

        return MarketRanking.builder()
                .rank(r.rank())
                .code(normalizeCode(r.symbol()))
                .currency(r.currency() != null ? Currency.fromString(r.currency()) : null)
                .lastPrice(lastPrice)
                .basePrice(toDecimal(r.price().basePrice()))
                .changeRate(toDecimal(r.price().changeRate()))
                .tradingVolume(toDecimal(r.tradingVolume()))
                .tradingAmount(toDecimal(r.tradingAmount()))
                .build();
    }

    /** 국내 종목이 'A005930' 형태로 오면 stock 테이블 코드(005930)에 맞춘다 */
    private static String normalizeCode(String symbol) {
        String s = symbol.trim();
        return s.matches("A\\d{6}") ? s.substring(1) : s;
    }

    private static BigDecimal toDecimal(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return new BigDecimal(s.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDateTime toKst(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(KST).toLocalDateTime();
    }
}
