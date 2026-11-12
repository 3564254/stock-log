package com.stocklog.modules.stock.momentum;

import com.stocklog.modules.stock.chart.StockChartService;
import com.stocklog.modules.stock.chart.dto.StockChartDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** 미국 중기 모멘텀(12-1개월) 순위. 앱의 미국 종목 진입 판단의 중심 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MomentumService {

    public static final double CANDIDATE_PCT = 20.0;
    private static final long PAUSE_MS = 300;           // KIS 호출 간격 (스크리닝과 같은 값)

    private final StockChartService chartService;
    private final MomentumRankRepository repository;
    private final MomentumStore store;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile LocalDateTime lastRunAt;
    private volatile String lastRunNote = "";

    public record Universe(String symbol, String name, String sector) {}

    /** code: 앱 종목 코드, inUniverse: S&P500 목록에 있음, ranked: 오늘 순위가 있음 */
    public record MomentumView(String code, boolean inUniverse, boolean ranked, String name, String sector,
                               Double ret121, Integer rankNo, Double pct, Integer universeSize, boolean candidate,
                               String asOf, String nextRebalance) {}

    public record Status(boolean running, String lastRunAt, String note, String asOf, int count) {}

    public List<Universe> universe() {
        List<Universe> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new ClassPathResource("momentum/sp500.csv").getInputStream(), StandardCharsets.UTF_8))) {
            String line = r.readLine(); // 헤더
            while ((line = r.readLine()) != null) {
                String[] c = line.split(",", -1);
                if (c.length >= 3 && !c[0].isBlank()) out.add(new Universe(c[0].trim(), c[1].trim(), c[2].trim()));
            }
        } catch (Exception e) {
            log.error("[Momentum] 대상 목록을 읽지 못했어요", e);
        }
        return out;
    }

    public boolean isRunning() { return running.get(); }

    /** 전체 순위 계산 (몇 분 걸림). 이미 돌고 있으면 건너뜀 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void refresh() {
        if (!running.compareAndSet(false, true)) return;
        long t0 = System.currentTimeMillis();
        List<String> missing = new ArrayList<>();
        try {
            List<Universe> uni = universe();
            List<Object[]> scored = new ArrayList<>(); // {Universe, appCode, ret}
            for (Universe u : uni) {
                if (Thread.currentThread().isInterrupted()) break;
                Double ret = null; String used = null;
                for (String code : candidates(u.symbol())) {
                    try {
                        List<StockChartDto> w = chartService.getWeeklyChartDtoAsc(code);
                        ret = ret121(w);
                        used = code;
                        break;
                    } catch (Exception e) {
                        // 다음 표기로 다시 시도
                    }
                }
                if (ret == null) missing.add(u.symbol()); else scored.add(new Object[]{u, used, ret});
                try { Thread.sleep(PAUSE_MS); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
            scored.sort((a, b) -> Double.compare((double) b[2], (double) a[2]));
            int n = scored.size();
            LocalDate asOf = LocalDate.now();
            List<MomentumRank> rows = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Universe u = (Universe) scored.get(i)[0];
                rows.add(new MomentumRank((String) scored.get(i)[1], u.name(), u.sector(), asOf,
                        (double) scored.get(i)[2], i + 1, (i + 1) * 100.0 / n, n));
            }
            if (n >= 50) store.replace(asOf, rows);   // 너무 적게 받았으면(API 장애) 어제 순위를 유지
            lastRunNote = String.format("%d종목 계산, %d종목 실패%s, %d초", n, missing.size(),
                    missing.isEmpty() ? "" : " (" + String.join(",", missing.subList(0, Math.min(10, missing.size()))) + (missing.size() > 10 ? "…" : "") + ")",
                    (System.currentTimeMillis() - t0) / 1000);
            log.info("[Momentum] {}", lastRunNote);
        } catch (Exception e) {
            lastRunNote = "실패: " + e.getMessage();
            log.error("[Momentum] 순위 계산 실패", e);
        } finally {
            lastRunAt = LocalDateTime.now();
            running.set(false);
        }
    }

    /** 주봉 종가 기준 12-1개월 수익률. 주봉이 53개 미만이면 계산 불가 */
    static Double ret121(List<StockChartDto> w) {
        if (w == null || w.size() < 53) return null;
        int last = w.size() - 1;
        double c4 = w.get(last - 4).close().doubleValue(), c52 = w.get(last - 52).close().doubleValue();
        return c52 > 0 && c4 > 0 ? c4 / c52 - 1 : null;
    }

    private static List<String> candidates(String symbol) {
        LinkedHashSet<String> s = new LinkedHashSet<>();
        s.add(symbol);
        if (symbol.contains(".")) { s.add(symbol.replace('.', '/')); s.add(symbol.replace('.', '-')); s.add(symbol.replace(".", "")); }
        return new ArrayList<>(s);
    }

    @Transactional(readOnly = true)
    public boolean hasData() { return repository.findLatestAsOf() != null; }

    @Transactional(readOnly = true)
    public MomentumView view(String code) {
        String c = code == null ? "" : code.trim().toUpperCase();
        boolean inUni = universe().stream().anyMatch(u -> candidates(u.symbol()).contains(c));
        LocalDate asOf = repository.findLatestAsOf();
        String next = nextRebalance().toString();
        if (asOf == null) return new MomentumView(c, inUni, false, null, null, null, null, null, null, false, null, next);
        return repository.findFirstByAsOfAndCode(asOf, c)
                .map(m -> new MomentumView(c, true, true, m.getName(), m.getSector(), m.getRet121(), m.getRankNo(), m.getPct(),
                        m.getUniverseSize(), m.getPct() <= CANDIDATE_PCT, asOf.toString(), next))
                .orElse(new MomentumView(c, inUni, false, null, null, null, null, null, null, false, asOf.toString(), next));
    }

    @Transactional(readOnly = true)
    public List<MomentumView> top() {
        LocalDate asOf = repository.findLatestAsOf();
        if (asOf == null) return List.of();
        String next = nextRebalance().toString();
        return repository.findByAsOfOrderByRankNoAsc(asOf).stream()
                .filter(m -> m.getPct() <= CANDIDATE_PCT)
                .map(m -> new MomentumView(m.getCode(), true, true, m.getName(), m.getSector(), m.getRet121(), m.getRankNo(), m.getPct(),
                        m.getUniverseSize(), true, asOf.toString(), next))
                .toList();
    }

    @Transactional(readOnly = true)
    public Status status() {
        LocalDate asOf = repository.findLatestAsOf();
        int cnt = asOf == null ? 0 : repository.findByAsOfOrderByRankNoAsc(asOf).size();
        return new Status(running.get(), lastRunAt == null ? null : lastRunAt.toString(), lastRunNote, asOf == null ? null : asOf.toString(), cnt);
    }

    /** 다음 분기 첫날 (1/4/7/10월 1일). 백테스트는 분기 첫 거래일 종가로 순위를 정하고 다음날 교체했다 */
    public static LocalDate nextRebalance() {
        return nextRebalance(LocalDate.now(java.time.ZoneId.of("America/New_York")));
    }

    /** 다음 분기 교체일 = 분기 첫 '거래일'(미국 날짜). 예전엔 분기 첫날을 그대로 돌려줬다 */
    static LocalDate nextRebalance(LocalDate t) {
        int qm = ((t.getMonthValue() - 1) / 3) * 3 + 1;
        LocalDate cur = firstTradingDay(t.getYear(), qm);
        if (t.isBefore(cur)) return cur;
        return qm + 3 > 12 ? firstTradingDay(t.getYear() + 1, 1) : firstTradingDay(t.getYear(), qm + 3);
    }

    /** 그 달의 첫 거래일 근사: 주말과 새해 첫날을 건너뛴다 */
    static LocalDate firstTradingDay(int year, int month) {
        LocalDate d = LocalDate.of(year, month, 1);
        while (true) {
            java.time.DayOfWeek w = d.getDayOfWeek();
            boolean weekend = w == java.time.DayOfWeek.SATURDAY || w == java.time.DayOfWeek.SUNDAY;
            boolean newYear = month == 1 && (d.getDayOfMonth() == 1 || (d.getDayOfMonth() == 2 && w == java.time.DayOfWeek.MONDAY));
            if (!weekend && !newYear) return d;
            d = d.plusDays(1);
        }
    }
}
