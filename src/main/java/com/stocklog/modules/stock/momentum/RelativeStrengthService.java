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

/** 6개월 상대 강도 (미국 종목, 참고 정보) */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelativeStrengthService {

    private static final int DAYS = 126;
    private static final int WEEKS = 26;
    private static final String INDEX = "SPY";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final StockChartService chartService;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public record RsView(String code, boolean known, Double ret6m, Double indexRet6m, Boolean strong,
                         boolean approx, String indexName, String asOf) {}

    private record Cached(RsView v, Instant at) {}

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RsView view(String code) {
        String c = code == null ? "" : code.trim().toUpperCase();
        Cached hit = cache.get(c);
        if (hit != null && hit.at().plus(TTL).isAfter(Instant.now())) return hit.v();
        RsView v = compute(c);
        if (v.known()) cache.put(c, new Cached(v, Instant.now()));
        return v;
    }

    private RsView compute(String code) {
        try {
            List<StockChartDto> s = chartService.getDailyChartDtoAsc(code);
            List<StockChartDto> m = chartService.getDailyChartDtoAsc(INDEX);
            boolean approx = false;
            if (s == null || m == null || s.size() <= DAYS || m.size() <= DAYS) {
                s = chartService.getWeeklyChartDtoAsc(code);
                m = chartService.getWeeklyChartDtoAsc(INDEX);
                approx = true;
            }
            int back = approx ? WEEKS : DAYS;
            if (s == null || m == null || s.size() <= back || m.size() <= back) return unknown(code);
            StockChartDto last = s.get(s.size() - 1), first = s.get(s.size() - 1 - back);
            double r = last.close().doubleValue() / first.close().doubleValue() - 1;
            // 지수는 종목과 같은 날짜 범위
            Double mi0 = null, mi1 = null;
            for (StockChartDto d : m) {
                if (d.date() == null) continue;
                if (mi0 == null && !d.date().isBefore(first.date())) mi0 = d.close().doubleValue();
                if (!d.date().isAfter(last.date())) mi1 = d.close().doubleValue();
            }
            if (mi0 == null || mi1 == null || mi0 <= 0) return unknown(code);
            double ri = mi1 / mi0 - 1;
            return new RsView(code, true, r, ri, r > ri, approx, INDEX, last.date() != null ? last.date().toString() : null);
        } catch (Exception e) {
            log.warn("[상대 강도] {} 계산 실패: {}", code, e.getMessage());
            return unknown(code);
        }
    }

    private RsView unknown(String code) { return new RsView(code, false, null, null, null, false, INDEX, null); }
}
