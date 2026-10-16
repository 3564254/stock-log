package com.stocklog.modules.market.ranking;

import com.stocklog.modules.common.type.Nation;
import com.stocklog.modules.market.open.MarketOpenInfo;
import com.stocklog.modules.market.open.MarketOpenService;
import com.stocklog.modules.market.ranking.MarketRankingInfo.MarketRanking;
import com.stocklog.modules.stock.stock.Stock;
import com.stocklog.modules.stock.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketRankingService {

    /** 메인 화면을 열 때마다 토스를 부르지 않도록 시장별로 잠깐 들고 있는다 */
    private static final Duration TTL = Duration.ofSeconds(60);
    private static final int MAX_SIZE = 20;

    private final MarketRankingProvider provider;
    private final MarketOpenService openService;
    private final StockRepository stockRepository;

    private final Map<Nation, Cached> cache = new ConcurrentHashMap<>();
    private record Cached(Instant at, MarketRankingInfo info) {}

    /**
     * 거래대금 순위 상위 size개.
     * 국내 정규장 중이면 국내, 그 외에는 미국 실시간 순위
     */
    public MarketRankingInfo getMarketRankingInfo(int size) {
        Nation nation = pickNation();
        MarketRankingInfo info = getCached(nation);

        int n = Math.min(Math.max(size, 1), MAX_SIZE);
        List<MarketRanking> top = info.rankings() == null ? List.of()
                : info.rankings().stream().limit(n).toList();

        return MarketRankingInfo.builder()
                .nation(nation)
                .realtime(info.realtime())
                .rankedAt(info.rankedAt())
                .rankings(withNames(top))
                .build();
    }

    private MarketRankingInfo getCached(Nation nation) {
        Cached c = cache.get(nation);
        if (c != null && c.at().plus(TTL).isAfter(Instant.now())) return c.info();

        MarketRankingInfo info = provider.fetchMarketRankings(nation);
        if (info != null && info.rankedAt() != null) {           // 실패 결과는 캐시하지 않는다
            cache.put(nation, new Cached(Instant.now(), info));
            return info;
        }
        // 실패했으면 조금 오래된 순위라도 보여 준다
        return c != null ? c.info() : MarketRankingInfo.builder().nation(nation).rankings(List.of()).build();
    }

    /** 국내 정규장 중이면 국내, 그 외에는 미국 (미국 실시간이 비어 있으면 Provider가 1일 순위로 대체) */
    private Nation pickNation() {
        return isRegularOpen(Nation.KR) ? Nation.KR : Nation.US;
    }

    private boolean isRegularOpen(Nation nation) {
        try {
            MarketOpenInfo info = openService.getMarketOpenDto(nation);
            return info != null && info.marketOpenType() != null && info.marketOpenType().isRegularMarket();
        } catch (Exception e) {
            log.warn("[Ranking] {} 장 운영 여부 조회 실패: {}", nation, e.getMessage());
            return false;
        }
    }

    /** 토스 순위에는 종목명이 없다 → stock 테이블에서 한 번에 찾아 붙인다 (없으면 코드 그대로) */
    private List<MarketRanking> withNames(List<MarketRanking> rankings) {
        if (rankings.isEmpty()) return rankings;
        try {
            List<String> codes = rankings.stream().map(MarketRanking::code).distinct().toList();
            Map<String, String> names = stockRepository.findAllById(codes).stream()
                    .collect(Collectors.toMap(Stock::getCode, Stock::getName, (a, b) -> a));
            return rankings.stream()
                    .map(r -> r.withName(names.getOrDefault(r.code(), r.code())))
                    .toList();
        } catch (Exception e) {
            log.warn("[Ranking] 종목명 조회 실패: {}", e.getMessage());
            return rankings.stream().map(r -> r.withName(r.code())).toList();
        }
    }
}
