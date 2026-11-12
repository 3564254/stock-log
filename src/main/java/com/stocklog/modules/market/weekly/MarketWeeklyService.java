package com.stocklog.modules.market.weekly;

import com.fasterxml.jackson.databind.JsonNode;
import com.stocklog.infra.fred.config.FredProperties;
import com.stocklog.modules.market.analysis.diagnosis.MarketDiagnosisHistory;
import com.stocklog.modules.market.analysis.diagnosis.MarketDiagnosisHistoryRepository;
import com.stocklog.modules.stock.analysis.MarketRegimeService;
import com.stocklog.modules.stock.momentum.MomentumService;
import com.stocklog.modules.stock.momentum.SectorTrendService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

/** 시장 화면 '이번 주 대응 · 어디를 볼지'의 재료. 판정은 하지 않고 사실만 모은다 */
@Service
@Slf4j
public class MarketWeeklyService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final ZoneId NY = ZoneId.of("America/New_York");
    private static final Duration CACHE = Duration.ofMinutes(30);
    private static final Duration FRED_CACHE = Duration.ofHours(6);

    /** 연준 FOMC 결정일. 출처: federalreserve.gov FOMC 일정 */
    private static final List<LocalDate> FOMC = List.of(
            LocalDate.of(2026, 1, 28), LocalDate.of(2026, 3, 18), LocalDate.of(2026, 4, 29), LocalDate.of(2026, 6, 17),
            LocalDate.of(2026, 7, 29), LocalDate.of(2026, 9, 16), LocalDate.of(2026, 10, 28), LocalDate.of(2026, 12, 9),
            LocalDate.of(2027, 1, 27), LocalDate.of(2027, 3, 17), LocalDate.of(2027, 4, 28), LocalDate.of(2027, 6, 9),
            LocalDate.of(2027, 7, 28), LocalDate.of(2027, 9, 15), LocalDate.of(2027, 10, 27), LocalDate.of(2027, 12, 8));

    /** FRED 발표 이름 → 화면 이름 (이름 앞부분으로 찾는다) */
    private static final String[][] RELEASES = {
            {"Consumer Price Index", "미국 소비자물가(CPI)"},
            {"Employment Situation", "미국 고용지표"},
            {"Gross Domestic Product", "미국 GDP"},
            {"Personal Income and Outlays", "미국 PCE 물가 · 소비"},
            {"Producer Price Index", "미국 생산자물가(PPI)"},
    };

    private final MarketRegimeService regimeService;
    private final SectorTrendService sectorService;
    private final MarketDiagnosisHistoryRepository historyRepository;
    private final WebClient fredWebClient;
    private final FredProperties fredProperties;

    public MarketWeeklyService(MarketRegimeService regimeService, SectorTrendService sectorService,
                               MarketDiagnosisHistoryRepository historyRepository,
                               @Qualifier("fredWebClient") WebClient fredWebClient, FredProperties fredProperties) {
        this.regimeService = regimeService;
        this.sectorService = sectorService;
        this.historyRepository = historyRepository;
        this.fredWebClient = fredWebClient;
        this.fredProperties = fredProperties;
    }

    public record Regime(String market, String indexName, boolean known, boolean weak, double ddFromHigh) {}
    public record Change(String key, String name, double now, double prev, double delta, boolean flipped, String hint) {}
    public record Event(LocalDate date, String name, String kind, String note) {}
    public record Weekly(List<Regime> regimes,
                         String diagDate, String prevDate, String regimeNow, String regimePrev, int streakDays,
                         List<Change> changes, List<Change> changes5d,
                         List<SectorTrendService.SectorRow> sectors, Double spyRet6m,
                         String nextRebalance, long rebalanceDays,
                         List<Event> events, boolean eventsPartial) {}

    private volatile Weekly cached;
    private volatile Instant cachedAt = Instant.EPOCH;
    private volatile List<Event> fredEvents = List.of();
    private volatile Instant fredAt = Instant.EPOCH;
    private volatile boolean fredOk = false;

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public synchronized Weekly weekly() {
        if (cached != null && cachedAt.plus(CACHE).isAfter(Instant.now())) return cached;
        List<Regime> regimes = new ArrayList<>();
        for (String[] m : new String[][]{{"US", "SPY"}, {"KR", "005930"}}) {
            try {
                var r = regimeService.forStock(m[1]);
                regimes.add(r == null ? new Regime(m[0], null, false, false, 0)
                        : new Regime(m[0], r.indexName(), r.known(), r.weak(), r.ddFromHigh()));
            } catch (Exception e) {
                regimes.add(new Regime(m[0], null, false, false, 0));
            }
        }

        List<MarketDiagnosisHistory> hist = historyRepository.findAllByOrderByDateDesc(PageRequest.of(0, 6));
        String diagDate = null, prevDate = null, regimeNow = null, regimePrev = null;
        int streak = 0;
        List<Change> ch1 = List.of(), ch5 = List.of();
        if (!hist.isEmpty()) {
            MarketDiagnosisHistory now = hist.get(0);
            diagDate = String.valueOf(now.getDate());
            regimeNow = now.getRegime() == null ? null : now.getRegime().getDescription();
            streak = now.getRegimeStreakDays();
            if (hist.size() > 1) {
                MarketDiagnosisHistory prev = hist.get(1);
                prevDate = String.valueOf(prev.getDate());
                regimePrev = prev.getRegime() == null ? null : prev.getRegime().getDescription();
                ch1 = changes(now, prev);
            }
            if (hist.size() > 5) ch5 = changes(now, hist.get(5));
        }

        List<SectorTrendService.SectorRow> sectors = List.of();
        Double spy = null;
        try { sectors = sectorService.allSectors(); spy = sectorService.spyRet6m(); }
        catch (Exception e) { log.warn("[이번 주] 업종 ETF 실패: {}", e.getMessage()); }

        LocalDate today = LocalDate.now(KST);
        LocalDate next = MomentumService.nextRebalance();
        long days = java.time.temporal.ChronoUnit.DAYS.between(today, next);

        List<Event> events = new ArrayList<>();
        LocalDate todayNy = LocalDate.now(NY);
        for (LocalDate d : FOMC) {
            if (!d.isBefore(todayNy) && !d.isAfter(todayNy.plusDays(21)))
                events.add(new Event(d, "FOMC 금리 결정", "FOMC", "한국 시간 다음 날 새벽 3~4시 발표"));
        }
        events.addAll(fred(todayNy));
        events.sort(Comparator.comparing(Event::date));

        cached = new Weekly(regimes, diagDate, prevDate, regimeNow, regimePrev, streak, ch1, ch5, sectors, spy,
                next.toString(), days, events, !fredOk);
        cachedAt = Instant.now();
        return cached;
    }

    private static final Object[][] SCORES = {
            {"trend", "추세", (ToDoubleFunction<MarketDiagnosisHistory>) MarketDiagnosisHistory::getTrendScore, "지수가 이동평균 위에서 오르는지"},
            {"momentum", "모멘텀", (ToDoubleFunction<MarketDiagnosisHistory>) MarketDiagnosisHistory::getMomentumScore, "최근 상승 속도"},
            {"breadth", "시장 폭", (ToDoubleFunction<MarketDiagnosisHistory>) MarketDiagnosisHistory::getBreadthScore, "소수 종목만 오르는지, 넓게 오르는지"},
            {"rates", "금리", (ToDoubleFunction<MarketDiagnosisHistory>) MarketDiagnosisHistory::getRatesScore, "금리 · 장단기 금리차 부담"},
            {"liquidity", "유동성", (ToDoubleFunction<MarketDiagnosisHistory>) MarketDiagnosisHistory::getLiquidityScore, "시중 자금 흐름"},
            {"macro", "경기", (ToDoubleFunction<MarketDiagnosisHistory>) MarketDiagnosisHistory::getMacroScore, "경기 지표 방향"},
            {"credit", "신용", (ToDoubleFunction<MarketDiagnosisHistory>) MarketDiagnosisHistory::getCreditScore, "회사채 금리차 · 금융 스트레스"},
            {"sector", "업종 흐름", (ToDoubleFunction<MarketDiagnosisHistory>) MarketDiagnosisHistory::getSectorScore, "공격 업종이 이끄는지"},
            {"defensive", "방어 선호", (ToDoubleFunction<MarketDiagnosisHistory>) MarketDiagnosisHistory::getDefensiveScore, "필수소비재 · 금 · 달러로 피하는지"},
            {"crossAsset", "자산 간 흐름", (ToDoubleFunction<MarketDiagnosisHistory>) MarketDiagnosisHistory::getCrossAssetScore, "주식 · 채권 · 원자재가 같은 방향인지"},
    };

    /** 부호가 바뀌었거나 20점 이상 움직인 지표, 크게 움직인 순 최대 5개 */
    @SuppressWarnings("unchecked")
    private static List<Change> changes(MarketDiagnosisHistory now, MarketDiagnosisHistory prev) {
        List<Change> out = new ArrayList<>();
        for (Object[] s : SCORES) {
            ToDoubleFunction<MarketDiagnosisHistory> f = (ToDoubleFunction<MarketDiagnosisHistory>) s[2];
            double a = f.applyAsDouble(now), b = f.applyAsDouble(prev), d = a - b;
            boolean flipped = Math.signum(a) != Math.signum(b) && (Math.abs(a) >= 10 || Math.abs(b) >= 10);
            if (flipped || Math.abs(d) >= 20) out.add(new Change((String) s[0], (String) s[1], round(a), round(b), round(d), flipped, (String) s[3]));
        }
        out.sort(Comparator.comparingDouble((Change c) -> Math.abs(c.delta())).reversed());
        return out.size() > 5 ? out.subList(0, 5) : out;
    }

    private static double round(double v) { return Math.round(v * 10.0) / 10.0; }

    /** FRED 발표 일정 (앞으로 14일). 6시간 캐시, 실패하면 빈 목록 */
    private List<Event> fred(LocalDate from) {
        if (fredAt.plus(FRED_CACHE).isAfter(Instant.now())) return fredEvents;
        fredAt = Instant.now();
        String key = fredProperties.getApiKey();
        if (key == null || key.isBlank() || key.startsWith("{")) { fredOk = false; return fredEvents = List.of(); }
        try {
            LocalDate to = from.plusDays(14);
            JsonNode root = fredWebClient.get()
                    .uri(u -> u.path("/fred/releases/dates")
                            .queryParam("api_key", key).queryParam("file_type", "json")
                            .queryParam("realtime_start", from.toString()).queryParam("realtime_end", to.toString())
                            .queryParam("include_release_dates_with_no_data", "true")
                            .queryParam("sort_order", "asc").queryParam("limit", "1000").build())
                    .retrieve().bodyToMono(JsonNode.class).block(Duration.ofSeconds(10));
            List<Event> out = new ArrayList<>();
            if (root != null && root.path("release_dates").isArray()) {
                for (JsonNode n : root.path("release_dates")) {
                    String name = n.path("release_name").asText(""), date = n.path("date").asText("");
                    for (String[] r : RELEASES) {
                        if (name.startsWith(r[0]) && !date.isBlank()) {
                            LocalDate d = LocalDate.parse(date);
                            if (!d.isBefore(from) && !d.isAfter(to) && out.stream().noneMatch(e -> e.name().equals(r[1]) && e.date().equals(d)))
                                out.add(new Event(d, r[1], "MACRO", "보통 미국 동부 오전 8:30 · 한국 시간 밤 10:30 (서머타임 땐 9:30)"));
                        }
                    }
                }
            }
            fredOk = true;
            return fredEvents = out;
        } catch (Exception e) {
            log.warn("[이번 주] FRED 발표 일정 실패: {}", e.getMessage());
            fredOk = false;
            return fredEvents = List.of();
        }
    }
}
