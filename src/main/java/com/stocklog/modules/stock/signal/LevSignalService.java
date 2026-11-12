package com.stocklog.modules.stock.signal;

import com.stocklog.modules.common.type.Period;
import com.stocklog.modules.market.index.repository.MarketIndexHistoryRepository;
import com.stocklog.modules.stock.chart.StockChart;
import com.stocklog.modules.stock.chart.StockChartRepository;
import com.stocklog.modules.stock.chart.StockChartService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** 레버리지 단타 신호 */
@Slf4j
@Service
public class LevSignalService {

    /** 기초지수와 거래할 상품. k: 대표 레버리지 배수 */
    public record Under(String code, String name, boolean kr, String lev, int k, String inv, int ki) {}

    public static final List<Under> UNDERS = List.of(
            new Under("QQQ", "나스닥100", false, "TQQQ · QLD", 3, "SQQQ", -3),
            new Under("SPY", "S&P500", false, "UPRO · SSO", 3, "SPXS", -3),
            new Under("SOXX", "반도체", false, "SOXL · USD", 3, "SOXS", -3),
            new Under("XLK", "기술", false, "TECL", 3, null, 0),
            new Under("IWM", "중소형주", false, "TNA", 3, "TZA", -3),
            new Under("XLF", "금융", false, "FAS", 3, null, 0),
            new Under("XBI", "바이오", false, "LABU", 3, "LABD", -3),
            new Under("XLE", "에너지", false, "ERX", 2, null, 0),
            new Under("TLT", "미국 장기채", false, "TMF", 3, null, 0),
            new Under("069500", "코스피200", true, "KODEX 레버리지(122630)", 2, null, 0),
            new Under("229200", "코스닥150", true, "코스닥150 레버리지(233740)", 2, null, 0)
    );

    /** 한 기초지수의 오늘 상태 */
    public record Row(String code, String name, boolean kr, String lev, int k, String inv, int ki,
                      LocalDate asOf, Double close, Double rsi2, Boolean up, Double ma200, String state,
                      Double bullTrigger, Double bullTriggerPct, Double bullFloor,
                      Double invTrigger, Double invTriggerPct, Double invCeil) {}

    public record Board(Instant updatedAt, Double vix, Double vixAvg10, LocalDate vixDate, Double vixNeedTonight, List<Row> rows) {}

    private final StockChartService chartService;
    private final StockChartRepository chartRepository;
    private final Rsi2Service rsi2Service;
    private final MarketIndexHistoryRepository indexHistoryRepository;

    private volatile Board board;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lev-signal");
        t.setDaemon(true);
        return t;
    });

    public LevSignalService(StockChartService chartService, StockChartRepository chartRepository,
                            Rsi2Service rsi2Service, MarketIndexHistoryRepository indexHistoryRepository) {
        this.chartService = chartService;
        this.chartRepository = chartRepository;
        this.rsi2Service = rsi2Service;
        this.indexHistoryRepository = indexHistoryRepository;
    }

    @PreDestroy
    void shutdown() { worker.shutdownNow(); }

    /** 서버 켤 때 한 번 */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try { board = compute(); } catch (Exception e) { log.warn("[LevSignal] 첫 계산 실패: {}", e.getMessage()); }
        refreshAsync(null);
    }

    /** 미국 마감 뒤 */
    @Scheduled(cron = "0 20 7 * * TUE-SAT", zone = "Asia/Seoul")
    public void afterUsClose() { refreshAsync(Boolean.FALSE); }

    /** 국내 마감(15:30) 뒤 */
    @Scheduled(cron = "0 45 15 * * MON-FRI", zone = "Asia/Seoul")
    public void afterKrClose() { refreshAsync(Boolean.TRUE); }

    /** 오후에 FRED VIX가 늦게 들어오는 경우를 위해 저녁에 한 번 더 */
    @Scheduled(cron = "0 30 18,22 * * MON-FRI", zone = "Asia/Seoul")
    public void recomputeEvening() {
        try { board = compute(); } catch (Exception e) { log.warn("[LevSignal] 재계산 실패: {}", e.getMessage()); }
    }

    public Board board() {
        Board b = board;
        if (b == null) { b = compute(); board = b; }
        return b;
    }

    /** kr: null = 전부, true = 국내만, false = 미국만 */
    public void refreshAsync(Boolean kr) {
        if (!running.compareAndSet(false, true)) return;
        worker.submit(() -> {
            try {
                for (Under u : UNDERS) {
                    if (kr != null && u.kr() != kr) continue;
                    try { chartService.refresh(u.code(), Period.DAY); }
                    catch (Exception e) { log.warn("[LevSignal] {} 일봉 갱신 실패: {}", u.code(), e.getMessage()); }
                    // 일봉이 200개 안 되면 큰 흐름은 주봉 40주선으로 본다
                    try { chartService.refresh(u.code(), Period.WEEK); }
                    catch (Exception e) { log.warn("[LevSignal] {} 주봉 갱신 실패: {}", u.code(), e.getMessage()); }
                }
                board = compute();
                long sig = board.rows().stream().filter(r -> "SIGNAL".equals(r.state())).count();
                log.info("[LevSignal] 갱신 완료 (강한 신호 {}개)", sig);
            } catch (Exception e) {
                log.warn("[LevSignal] 갱신 실패: {}", e.getMessage());
            } finally {
                running.set(false);
            }
        });
    }

    Board compute() {
        // VIX: 최신값 + 직전 10일 평균, 오늘 밤 기준 평균(최신 포함 10개)
        Double vix = null, avgPrev = null, avgTonight = null; LocalDate vixDate = null;
        try {
            var h = indexHistoryRepository.findHistoryDESCTopN("VIXCLS", 11);
            List<Double> v = new ArrayList<>();
            for (var x : h) if (x.getCloseValue() != null) v.add(x.getCloseValue().doubleValue());
            if (!h.isEmpty()) vixDate = h.get(0).getDate();
            if (!v.isEmpty()) vix = v.get(0);
            if (v.size() >= 11) avgPrev = v.subList(1, 11).stream().mapToDouble(d -> d).average().orElse(Double.NaN);
            if (v.size() >= 10) avgTonight = v.subList(0, 10).stream().mapToDouble(d -> d).average().orElse(Double.NaN);
        } catch (Exception e) { log.debug("[LevSignal] VIX 조회 실패: {}", e.getMessage()); }
        Double need = avgTonight == null ? null : Math.max(20.0, avgTonight * 1.1);

        List<Row> rows = new ArrayList<>();
        for (Under u : UNDERS) {
            try { rows.add(row(u)); }
            catch (Exception e) {
                rows.add(new Row(u.code(), u.name(), u.kr(), u.lev(), u.k(), u.inv(), u.ki(), null, null, null, null, null, "NONE",
                        null, null, null, null, null, null));
            }
        }
        return new Board(Instant.now(), vix, avgPrev, vixDate, need, rows);
    }

    private Row row(Under u) {
        List<StockChart> day = chartRepository.findByCodeAndPeriodOrderByTradeDateAsc(u.code(), Period.DAY);
        double[] c = day.stream().map(StockChart::getClosePrice).filter(v -> v != null && v.signum() > 0).mapToDouble(BigDecimal::doubleValue).toArray();
        Rsi2Service.Rsi2 r = rsi2Service.of(u.code());
        if (r == null || c.length < 10 || r.up() == null) {
            return new Row(u.code(), u.name(), u.kr(), u.lev(), u.k(), u.inv(), u.ki(), r == null ? null : r.asOf(), r == null ? null : r.close(),
                    r == null ? null : r.rsi2(), r == null ? null : r.up(), null, "NONE", null, null, null, null, null, null);
        }
        double last = c[c.length - 1];
        double ma, floor;
        if (c.length >= 200) {
            ma = 0; for (int i = c.length - 200; i < c.length; i++) ma += c[i]; ma /= 200;
            double sum199 = 0; for (int i = c.length - 199; i < c.length; i++) sum199 += c[i];
            floor = sum199 / 199;   // 오늘 종가 P가 들어간 200일 평균보다 P가 높으려면 P > sum199/199
        } else {
            // 일봉이 부족하면 주봉 40주선으로 대신한다
            List<StockChart> wk = chartRepository.findByCodeAndPeriodOrderByTradeDateAsc(u.code(), Period.WEEK);
            double[] w = wk.stream().map(StockChart::getClosePrice).filter(v -> v != null && v.signum() > 0).mapToDouble(BigDecimal::doubleValue).toArray();
            if (w.length < 41) {
                return new Row(u.code(), u.name(), u.kr(), u.lev(), u.k(), u.inv(), u.ki(), r.asOf(), r.close(), r.rsi2(), r.up(), null, "NONE",
                        null, null, null, null, null, null);
            }
            ma = 0; for (int i = w.length - 40; i < w.length; i++) ma += w[i]; ma /= 40;
            LocalDate lastWeek = wk.get(wk.size() - 1).getTradeDate();
            boolean curWeek = lastWeek.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR) == LocalDate.now().get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                    && !lastWeek.isBefore(LocalDate.now().minusDays(7));
            int end = curWeek ? w.length - 1 : w.length;
            double s39 = 0; for (int i = end - 39; i < end; i++) s39 += w[i];
            floor = s39 / 39;
        }

        // Wilder 2일 상태
        double ag = 0, al = 0;
        for (int i = 1; i < c.length; i++) {
            double ch = c[i] - c[i - 1], g = Math.max(ch, 0), l = Math.max(-ch, 0);
            if (i <= 2) { ag += g / 2; al += l / 2; } else { ag = (ag + g) / 2; al = (al + l) / 2; }
        }
        // 다음 종가가 이 값 이하면 RSI2 < 10
        double bullTrig = al >= 9 * ag ? last + (al / 9 - ag) : last - (9 * ag - al);
        // 다음 종가가 이 값 이상이면 RSI2 > 90
        double invTrig = ag >= 9 * al ? last - (ag / 9 - al) : last + (9 * al - ag);

        String state;
        if (Boolean.TRUE.equals(r.up())) state = r.spike() ? "SIGNAL" : r.fear() ? "WEAK" : r.dip() ? "DIP" : "WAIT";
        else if (Boolean.FALSE.equals(r.up())) state = (r.rally() && u.inv() != null && !u.kr()) ? "INV" : "OFF";
        else state = "NONE";

        boolean upNow = Boolean.TRUE.equals(r.up());
        return new Row(u.code(), u.name(), u.kr(), u.lev(), u.k(), u.inv(), u.ki(), r.asOf(), last, r.rsi2(), r.up(), round(ma), state,
                upNow ? round(bullTrig) : null, upNow ? round((bullTrig / last - 1) * 100) : null, upNow ? round(floor) : null,
                (!upNow && u.inv() != null && !u.kr()) ? round(invTrig) : null,
                (!upNow && u.inv() != null && !u.kr()) ? round((invTrig / last - 1) * 100) : null,
                (!upNow && u.inv() != null && !u.kr()) ? round(floor) : null);
    }

    private static Double round(double v) { return Double.isFinite(v) ? Math.round(v * 100) / 100.0 : null; }
}
