package com.stocklog.modules.stock.analysis;

import com.stocklog.modules.stock.chart.StockChartService;
import com.stocklog.modules.stock.chart.dto.StockChartDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 종목이 속한 시장의 큰 흐름: 지수(ETF로 대신) 종가가 주봉 40주 평균 아래면 '약세' */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketRegimeService {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final String KR_PROXY = "069500";
    private static final String US_PROXY = "SPY";

    private final StockChartService chartService;

    /** weak: 지수가 40주선 아래 */
    public record MarketRegime(String market, String indexName, boolean known, boolean weak,
                               double price, double ma40w, double ddFromHigh) {}

    private record Cached(MarketRegime v, Instant at) {}
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MarketRegime forStock(String stockCode) {
        boolean kr = stockCode != null && stockCode.matches("\\d+");
        String key = kr ? "KR" : "US";
        Cached c = cache.get(key);
        if (c != null && c.at().plus(TTL).isAfter(Instant.now())) return c.v();
        MarketRegime v = compute(key, kr ? KR_PROXY : US_PROXY, kr ? "코스피200" : "S&P500");
        if (v.known()) cache.put(key, new Cached(v, Instant.now()));
        return v;
    }

    private MarketRegime compute(String market, String proxy, String name) {
        try {
            List<StockChartDto> w = chartService.getWeeklyChartDtoAsc(proxy);
            if (w == null || w.size() < 40) return new MarketRegime(market, name, false, false, 0, 0, 0);
            int n = w.size();
            double sum = 0;
            for (int i = n - 40; i < n; i++) sum += w.get(i).close().doubleValue();
            double ma = sum / 40, price = w.get(n - 1).close().doubleValue(), hi = 0;
            for (int i = Math.max(0, n - 52); i < n; i++) hi = Math.max(hi, w.get(i).close().doubleValue());
            double dd = hi > 0 ? (price / hi - 1) * 100 : 0;
            return new MarketRegime(market, name, true, price < ma, price, ma, dd);
        } catch (Exception e) {
            log.warn("[시장 상태] {} 계산 실패: {}", market, e.getMessage());
            return new MarketRegime(market, name, false, false, 0, 0, 0);
        }
    }
}
