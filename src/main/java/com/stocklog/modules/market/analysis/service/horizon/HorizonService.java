package com.stocklog.modules.market.analysis.service.horizon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocklog.modules.market.analysis.diagnosis.MarketDiagnosisHistory;
import com.stocklog.modules.market.analysis.diagnosis.MarketDiagnosisHistoryRepository;
import com.stocklog.modules.market.analysis.dto.EngineInput;
import com.stocklog.modules.market.analysis.service.analyzer.ShortTermShockDetector.ShockDetails;
import com.stocklog.modules.market.analysis.service.result.MarketAnalyzerScore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 시간축별 시장 판정 (단기 1~2주 · 중기 1~3개월 · 장기 6~12개월).
 *
 * 하나의 점수에 단기 흐름과 중장기 바탕이 섞이면 "시장은 급락했는데 분석은 좋다" 같은 어긋남이 생긴다.
 * 기존 분석기 점수를 시간축별로 다시 묶어 따로 판정한다.
 *
 * 판정 안정화(히스테리시스): 경계값 근처에서 라벨이 매일 뒤집히지 않도록
 * 강세/약세 진입은 ±25, 이탈은 ±10을 넘어야 한다. 이전 상태는 지난 진단 기록의 엔진 입력(JSON)을 다시 읽어 계산한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HorizonService {

    static final double ENTER = 25.0;
    static final double EXIT = 10.0;
    private static final int HISTORY_DAYS = 120;

    private final MarketDiagnosisHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    public enum Horizon {
        SHORT("단기", "1~2주"), MID("중기", "1~3개월"), LONG("장기", "6~12개월");
        public final String title; public final String span;
        Horizon(String title, String span) { this.title = title; this.span = span; }
    }

    public enum Stance {
        BULLISH("강세", "good"), NEUTRAL("중립", "neutral"), BEARISH("약세", "caution");
        public final String label; public final String tone;
        Stance(String label, String tone) { this.label = label; this.tone = tone; }
    }

    public record Driver(boolean positive, String title, String detail) {}

    public record HorizonView(
            String key, String title, String span,
            double score,           // -100 ~ 100
            String stance,          // BULLISH / NEUTRAL / BEARISH
            String label,           // 강세 / 중립 / 약세
            String tone,            // good / neutral / caution
            String streakText,      // "3일째" 또는 "어제 강세에서 바뀜"
            List<Driver> drivers
    ) {}

    public record HorizonReport(String summary, List<HorizonView> horizons) {}

    // ── 시간축별 구성: 분석기 이름(EngineInput 필드명)과 가중치 ──
    private record Part(String key, double weight) {}

    private static final Map<Horizon, List<Part>> PARTS = Map.of(
            Horizon.SHORT, List.of(new Part("momentum", 0.45), new Part("risk", 0.30), new Part("crossAsset", 0.25),
                                   new Part("shock", 0.80)),          // 단기 충격(0 / -30 / -50 / -80)은 그대로 크게 반영
            Horizon.MID,   List.of(new Part("trend", 0.40), new Part("breadth", 0.20), new Part("sector", 0.15),
                                   new Part("rates", 0.15), new Part("defensive", 0.10),
                                   new Part("divergence", 0.30)),     // 괴리 경고는 0 이하만 나오는 감점 항목
            Horizon.LONG,  List.of(new Part("macro", 0.35), new Part("liquidity", 0.35), new Part("credit", 0.30))
    );

    // 요인별 읽기 쉬운 이름과 방향별 설명
    private static final Map<String, String[]> NAMES = Map.ofEntries(
            Map.entry("momentum",   new String[]{"모멘텀", "단기 탄력이 강해요", "단기 탄력이 약해요"}),
            Map.entry("risk",       new String[]{"위험 심리", "위험 선호", "위험 회피"}),
            Map.entry("crossAsset", new String[]{"자산 간 흐름", "위험자산 우위", "안전자산 우위"}),
            Map.entry("trend",      new String[]{"추세", "이동평균 상승 흐름", "이동평균 하락 흐름"}),
            Map.entry("breadth",    new String[]{"확산도", "상승 종목 확산", "대형주 쏠림"}),
            Map.entry("sector",     new String[]{"섹터", "성장 섹터 주도", "방어 섹터 강세"}),
            Map.entry("rates",      new String[]{"금리", "금리 부담 완화", "금리 부담"}),
            Map.entry("defensive",  new String[]{"방어 자산", "방어 자산 약세", "방어 자산 강세"}),
            Map.entry("divergence", new String[]{"지표 괴리", "", "지표 간 괴리 경고"}),
            Map.entry("macro",      new String[]{"거시 경제", "경기 지표 개선", "경기 지표 둔화"}),
            Map.entry("liquidity",  new String[]{"유동성", "유동성 확대", "유동성 축소"}),
            Map.entry("credit",     new String[]{"신용", "신용 시장 안정", "신용 시장 불안"})
    );

    @Transactional(readOnly = true)
    public HorizonReport evaluate(EngineInput input) {
        LocalDate today = LocalDate.now();
        Map<String, Double> todayScores = scoresOf(input);

        // 지난 기록(오늘 제외)을 오래된 순으로 다시 판정해 이전 상태와 시작일을 구한다
        List<MarketDiagnosisHistory> past = new ArrayList<>(
                historyRepository.findTop120ByDateBeforeOrderByDateDesc(today));
        Collections.reverse(past);

        List<HorizonView> views = new ArrayList<>();
        for (Horizon h : Horizon.values()) {
            Stance state = null;
            LocalDate since = null;
            for (MarketDiagnosisHistory row : past) {
                Map<String, Double> s = scoresOf(row.getRawEngineInputJson());
                if (s.isEmpty()) continue;
                Stance next = step(state, score(h, s));
                if (next != state) since = row.getDate();
                state = next;
            }
            double now = score(h, todayScores);
            Stance prev = state;
            Stance cur = step(prev, now);
            if (cur != prev || since == null) since = today;

            String streak;
            if (prev != null && cur != prev) streak = "직전 " + prev.label + "에서 바뀜";
            else streak = (ChronoUnit.DAYS.between(since, today) + 1) + "일째";

            views.add(new HorizonView(h.name(), h.title, h.span, round1(now), cur.name(), cur.label, cur.tone,
                    streak, drivers(h, todayScores, input)));
        }
        return new HorizonReport(summary(views), views);
    }

    // ── 점수 계산 ──

    static double score(Horizon h, Map<String, Double> s) {
        double sum = 0;
        for (Part p : PARTS.get(h)) sum += s.getOrDefault(p.key(), 0.0) * p.weight();
        return Math.max(-100, Math.min(100, sum));
    }

    /** 히스테리시스: 진입은 ±ENTER, 이탈은 ±EXIT 안쪽으로 들어와야 */
    static Stance step(Stance prev, double score) {
        if (prev == Stance.BULLISH && score > EXIT) return Stance.BULLISH;
        if (prev == Stance.BEARISH && score < -EXIT) return Stance.BEARISH;
        if (score >= ENTER) return Stance.BULLISH;
        if (score <= -ENTER) return Stance.BEARISH;
        return Stance.NEUTRAL;
    }

    private static Map<String, Double> scoresOf(EngineInput in) {
        Map<String, Double> m = new HashMap<>();
        put(m, "trend", in.trend()); put(m, "momentum", in.momentum()); put(m, "breadth", in.breadth());
        put(m, "risk", in.risk()); put(m, "rates", in.rates()); put(m, "liquidity", in.liquidity());
        put(m, "macro", in.macro()); put(m, "credit", in.credit()); put(m, "sector", in.sector());
        put(m, "defensive", in.defensive()); put(m, "crossAsset", in.crossAsset());
        put(m, "divergence", in.divergence()); put(m, "shock", in.shock());
        return m;
    }

    private static void put(Map<String, Double> m, String k, MarketAnalyzerScore s) {
        if (s != null) m.put(k, s.score());
    }

    /** 진단 기록에 저장된 EngineInput JSON에서 분석기별 score만 읽는다 (없는 항목은 0) */
    private Map<String, Double> scoresOf(String json) {
        Map<String, Double> m = new HashMap<>();
        if (json == null || json.isBlank()) return m;
        try {
            JsonNode root = objectMapper.readTree(json);
            for (String k : List.of("trend", "momentum", "breadth", "risk", "rates", "liquidity", "macro",
                    "credit", "sector", "defensive", "crossAsset", "divergence", "shock")) {
                JsonNode v = root.path(k).path("score");
                if (v.isNumber()) m.put(k, v.asDouble());
            }
        } catch (Exception e) {
            log.debug("[시간축 판정] 지난 진단 JSON을 읽지 못했어요: {}", e.getMessage());
        }
        return m;
    }

    // ── 화면 설명 ──

    /** 그 시간축 점수를 가장 크게 움직인 요인 3개 */
    private List<Driver> drivers(Horizon h, Map<String, Double> s, EngineInput input) {
        List<Driver> list = new ArrayList<>();
        ShockDetails shock = (input.shock() != null && input.shock().rawDetails() instanceof ShockDetails d) ? d : null;

        record C(String key, double contribution) {}
        List<C> cs = new ArrayList<>();
        for (Part p : PARTS.get(h)) cs.add(new C(p.key(), s.getOrDefault(p.key(), 0.0) * p.weight()));
        cs.sort(Comparator.comparingDouble((C c) -> Math.abs(c.contribution())).reversed());

        for (C c : cs) {
            if (list.size() >= 3) break;
            if (Math.abs(c.contribution()) < 2) continue;      // 영향이 미미한 요인은 생략
            boolean pos = c.contribution() > 0;
            if (c.key().equals("shock")) {
                if (shock == null) continue;
                if (shock.sharpDrop()) list.add(new Driver(false, "5일 급락",
                        String.format("S&P %.1f%% · 나스닥 %.1f%%", shock.sp500Return5D(), shock.nasdaqReturn5D())));
                if (shock.volatilitySpike() && list.size() < 3) list.add(new Driver(false, "VIX 급등",
                        String.format("%.1f · 5일 새 %+.0f%%", shock.vixLevel(), shock.vixChange5D())));
                continue;
            }
            String[] n = NAMES.get(c.key());
            if (n == null) continue;
            list.add(new Driver(pos, n[0], pos ? n[1] : n[2]));
        }
        return list;
    }

    private static String summary(List<HorizonView> v) {
        Stance sh = Stance.valueOf(v.get(0).stance()), mid = Stance.valueOf(v.get(1).stance()), lg = Stance.valueOf(v.get(2).stance());
        if (sh == mid && mid == lg) {
            return switch (sh) {
                case BULLISH -> "단기부터 장기까지 모두 우호적인 흐름이에요.";
                case BEARISH -> "단기부터 장기까지 모두 부담스러운 흐름이에요.";
                default -> "뚜렷한 방향 없이 시간대마다 중립적인 흐름이에요.";
            };
        }
        boolean baseGood = lg == Stance.BULLISH || (lg != Stance.BEARISH && mid == Stance.BULLISH);
        boolean baseBad = lg == Stance.BEARISH || (lg != Stance.BULLISH && mid == Stance.BEARISH);
        if (sh == Stance.BEARISH && baseGood) return "중장기 바탕은 아직 괜찮지만, 단기 분위기는 나빠요.";
        if (sh == Stance.BULLISH && baseBad) return "단기 반등이 나오고 있지만, 중장기 바탕은 약해요.";
        return String.format("단기 %s · 중기 %s · 장기 %s로 시간대별 흐름이 엇갈려요.", sh.label, mid.label, lg.label);
    }

    private static double round1(double v) { return Math.round(v * 10) / 10.0; }
}
