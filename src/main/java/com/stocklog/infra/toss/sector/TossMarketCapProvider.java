package com.stocklog.infra.toss.sector;

import com.stocklog.infra.toss.common.TossResponse;
import com.stocklog.infra.toss.config.auth.TossTokenManager;
import com.stocklog.infra.toss.sector.TossSectorOutput.Sector;
import com.stocklog.infra.toss.sector.TossSectorStocksOutput.SectorStock;
import com.stocklog.modules.common.type.Nation;
import com.stocklog.modules.stock.screening.MarketCapProvider;
import com.stocklog.modules.stock.screening.MarketCapStock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** 토스에는 시가총액 순위 API가 없다 */
@Component
@Slf4j
public class TossMarketCapProvider implements MarketCapProvider {

    private static final String SECTORS_PATH = "/api/v1/sectors";
    private static final String SECTOR_STOCKS_PATH = "/api/v1/sectors/{sectorId}/stocks";
    private static final int TOP_DEPTH = 0;

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(15);
    private static final long PAUSE_MS = 200;   // 섹터 사이 간격
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final WebClient webClient;
    private final TossTokenManager tossTokenManager;

    /** 섹터 목록은 거의 안 바뀐다 → 하루 한 번만 받는다 (토스 권장) */
    private volatile List<String> topSectorIds = List.of();
    private volatile LocalDate sectorsFetchedOn;

    public TossMarketCapProvider(@Qualifier("tossWebClient") WebClient webClient, TossTokenManager tossTokenManager) {
        this.webClient = webClient;
        this.tossTokenManager = tossTokenManager;
    }

    @Override
    public List<MarketCapStock> fetchByMarketCap(Nation nation) {
        if (nation != Nation.KR && nation != Nation.US) return List.of();

        List<String> sectorIds = getTopSectorIds();
        if (sectorIds.isEmpty()) {
            log.warn("[MarketCap] 토스 섹터 목록이 비어 있어 시가총액 순위를 만들 수 없음");
            return List.of();
        }

        // 한 종목이 여러 섹터에 들어 있을 수 있다 → 종목코드로 합친다
        Map<String, MarketCapStock> merged = new HashMap<>();
        int failed = 0;
        for (String sectorId : sectorIds) {
            if (Thread.currentThread().isInterrupted()) break;   // 서버 종료 중
            try {
                for (SectorStock s : fetchSectorStocks(sectorId, nation)) {
                    MarketCapStock stock = toStock(s);
                    if (stock != null) merged.putIfAbsent(stock.code(), stock);
                }
            } catch (Exception e) {
                failed++;
                log.warn("[MarketCap] 토스 섹터 {} ({}) 구성 종목 조회 실패: {}", sectorId, nation, describe(e));
            }
            pause();
        }
        if (failed > 0) log.warn("[MarketCap] {} 섹터 {}개 중 {}개 실패", nation, sectorIds.size(), failed);

        return merged.values().stream()
                .filter(s -> s.marketCap() != null)
                .sorted(Comparator.comparing(MarketCapStock::marketCap).reversed())
                .toList();
    }

    private List<String> getTopSectorIds() {
        LocalDate today = LocalDate.now(KST);
        if (today.equals(sectorsFetchedOn) && !topSectorIds.isEmpty()) return topSectorIds;

        try {
            List<Sector> sectors = fetchSectors();
            List<String> ids = sectors.stream()
                    .filter(s -> s.sectorId() != null && Objects.equals(s.depth(), TOP_DEPTH))
                    .map(Sector::sectorId)
                    .toList();
            if (ids.isEmpty()) {
                // 대분류가 안 오면 전체 섹터라도 돈다 (중복은 합칠 때 빠진다)
                ids = sectors.stream().map(Sector::sectorId).filter(Objects::nonNull).toList();
            }
            topSectorIds = ids;
            sectorsFetchedOn = today;
            log.info("[MarketCap] 토스 대분류 섹터 {}개", ids.size());
        } catch (Exception e) {
            log.warn("[MarketCap] 토스 섹터 목록 조회 실패 → 이전 목록 사용({}개): {}", topSectorIds.size(), describe(e));
        }
        return topSectorIds;
    }

    private List<Sector> fetchSectors() {
        TossResponse<TossSectorOutput> response = webClient.get()
                .uri(SECTORS_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tossTokenManager.getAccessToken())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<TossResponse<TossSectorOutput>>() {})
                .block(CALL_TIMEOUT);
        TossSectorOutput out = response != null ? response.getResult() : null;
        return out != null && out.sectors() != null ? out.sectors() : List.of();
    }

    private List<SectorStock> fetchSectorStocks(String sectorId, Nation nation) {
        TossResponse<TossSectorStocksOutput> response = webClient.get()
                .uri(b -> b.path(SECTOR_STOCKS_PATH)
                        .queryParam("marketCountry", nation.name())
                        .build(sectorId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tossTokenManager.getAccessToken())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<TossResponse<TossSectorStocksOutput>>() {})
                .block(CALL_TIMEOUT);
        TossSectorStocksOutput out = response != null ? response.getResult() : null;
        return out != null && out.stocks() != null ? out.stocks() : List.of();
    }

    private static MarketCapStock toStock(SectorStock s) {
        if (s == null || s.symbol() == null || s.symbol().isBlank()) return null;
        BigDecimal cap = s.marketCapitalization();
        return new MarketCapStock(normalizeCode(s.symbol()), s.name(), cap != null && cap.signum() > 0 ? cap : null);
    }

    /** 국내 종목이 'A005930' 형태로 오면 stock 테이블 코드(005930)에 맞춘다 */
    private static String normalizeCode(String symbol) {
        String s = symbol.trim();
        return s.matches("A\\d{6}") ? s.substring(1) : s;
    }

    private static String describe(Exception e) {
        if (e instanceof WebClientResponseException w) {
            return "HTTP " + w.getStatusCode() + " - " + w.getResponseBodyAsString();
        }
        return e.toString();
    }

    private static void pause() {
        try {
            Thread.sleep(PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
