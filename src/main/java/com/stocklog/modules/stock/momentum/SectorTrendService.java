package com.stocklog.modules.stock.momentum;

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

/** 업종 추세 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SectorTrendService {

    private static final int MA_WEEKS = 40;
    private static final int RET_WEEKS = 26;
    private static final Duration TTL = Duration.ofMinutes(30);

    /** GICS 업종 → (ETF, 한국어 이름) */
    private static final Map<String, String[]> ETF = Map.ofEntries(
            Map.entry("Information Technology", new String[]{"XLK", "정보기술"}),
            Map.entry("Financials", new String[]{"XLF", "금융"}),
            Map.entry("Health Care", new String[]{"XLV", "헬스케어"}),
            Map.entry("Consumer Discretionary", new String[]{"XLY", "경기소비재"}),
            Map.entry("Consumer Staples", new String[]{"XLP", "필수소비재"}),
            Map.entry("Energy", new String[]{"XLE", "에너지"}),
            Map.entry("Utilities", new String[]{"XLU", "유틸리티"}),
            Map.entry("Real Estate", new String[]{"XLRE", "부동산"}),
            Map.entry("Materials", new String[]{"XLB", "소재"}),
            Map.entry("Industrials", new String[]{"XLI", "산업재"}),
            Map.entry("Communication Services", new String[]{"XLC", "커뮤니케이션"})
    );

    private final MomentumService momentumService;
    private final StockChartService chartService;

    /** known=false면 화면에서 줄을 숨긴다 */
    public record SectorTrend(String code, boolean known, String sector, String sectorKo, String etf,
                              Boolean above40w, Double etfRet6m, Double spyRet6m, String asOf) {}

    private record Etf(boolean known, Boolean above, Double ret, String asOf, Instant at) {}
    private final Map<String, Etf> cache = new ConcurrentHashMap<>();

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SectorTrend view(String code) {
        String c = code == null ? "" : code.trim().toUpperCase();
        String sector = momentumService.universe().stream()
                .filter(u -> u.symbol().equalsIgnoreCase(c) || u.symbol().replace('.', '/').equalsIgnoreCase(c)
                        || u.symbol().replace('.', '-').equalsIgnoreCase(c) || u.symbol().replace(".", "").equalsIgnoreCase(c))
                .map(MomentumService.Universe::sector).findFirst().orElse(null);
        String[] e = sector == null ? null : ETF.get(sector.trim());
        if (e == null) return new SectorTrend(c, false, sector, null, null, null, null, null, null);
        Etf x = etf(e[0]), spy = etf("SPY");
        if (!x.known()) return new SectorTrend(c, false, sector, e[1], e[0], null, null, null, null);
        return new SectorTrend(c, true, sector, e[1], e[0], x.above(), x.ret(), spy.known() ? spy.ret() : null, x.asOf());
    }

    /** 업종 ETF 한 줄 (시장 화면 '업종 흐름'). known=false면 데이터 없음 */
    public record SectorRow(String etf, String sectorKo, boolean known, Boolean above40w, Double ret6m, String asOf) {}

    /** 11개 업종 ETF 전체 + SPY 26주 수익률. 각 ETF 30분 캐시 (주봉 조회 12번) */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public java.util.List<SectorRow> allSectors() {
        java.util.List<SectorRow> out = new java.util.ArrayList<>();
        for (String[] e : ETF.values()) {
            Etf x = etf(e[0]);
            out.add(new SectorRow(e[0], e[1], x.known(), x.above(), x.ret(), x.asOf()));
        }
        out.sort(java.util.Comparator.comparing((SectorRow r) -> r.ret6m() == null ? Double.NEGATIVE_INFINITY : r.ret6m()).reversed());
        return out;
    }

    /** SPY 26주 수익률 (모르면 null) */
    public Double spyRet6m() {
        Etf spy = etf("SPY");
        return spy.known() ? spy.ret() : null;
    }

    private Etf etf(String sym) {
        Etf hit = cache.get(sym);
        if (hit != null && hit.at().plus(TTL).isAfter(Instant.now())) return hit;
        Etf v = compute(sym);
        if (v.known()) cache.put(sym, v);
        return v;
    }

    private Etf compute(String sym) {
        try {
            List<StockChartDto> w = chartService.getWeeklyChartDtoAsc(sym);
            if (w == null || w.size() <= Math.max(MA_WEEKS, RET_WEEKS)) return new Etf(false, null, null, null, Instant.now());
            int n = w.size();
            double last = w.get(n - 1).close().doubleValue();
            double sum = 0;
            for (int i = n - MA_WEEKS; i < n; i++) sum += w.get(i).close().doubleValue();
            double ma = sum / MA_WEEKS;
            double first = w.get(n - 1 - RET_WEEKS).close().doubleValue();
            if (ma <= 0 || first <= 0) return new Etf(false, null, null, null, Instant.now());
            String asOf = w.get(n - 1).date() != null ? w.get(n - 1).date().toString() : null;
            return new Etf(true, last > ma, last / first - 1, asOf, Instant.now());
        } catch (Exception ex) {
            log.warn("[업종 추세] {} 계산 실패: {}", sym, ex.getMessage());
            return new Etf(false, null, null, null, Instant.now());
        }
    }
}
