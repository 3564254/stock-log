package com.stocklog.modules.stock.signal;

import com.stocklog.modules.common.type.Nation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 신호 성과 집계 (전향적 기록, 가상 추적) */
@Service
@RequiredArgsConstructor
public class SignalStatsService {

    static final int MIN_EVENTS = 100, MIN_WEEKS = 20;
    static final double T_LINE = 2.0;
    private final SignalRecordRepository repository;

    public record Row(String key, String label, int events, int tracked, int weeks,
                      Double mean10, Double median10, Double mean20, Double median20, Double x20Mean, Double x20Median,
                      Double mfe20, Double mae20, Double stopHitRate, Double targetHitRate,
                      Double vsSameStock, Double tSameStock, Double vsSameDay, Double tSameDay, String status) {}

    public record Stats(String nation, String engineVersion, String trackVersion, long records, long done,
                        String firstDate, String lastDate, List<Row> rows, String note) {}

    @Transactional(readOnly = true)
    public Stats stats(Nation nation) {
        List<SignalRecord> all = repository.findByNationAndEngineVersionAndTrackVersionOrderByStockCodeAscSignalDateAsc(nation, SignalVersion.ENGINE, SignalVersion.TRACK);
        long done = all.stream().filter(r -> "DONE".equals(r.getOutcomeStatus())).count();
        String first = all.stream().map(SignalRecord::getSignalDate).min(Comparator.naturalOrder()).map(LocalDate::toString).orElse(null);
        String last = all.stream().map(SignalRecord::getSignalDate).max(Comparator.naturalOrder()).map(LocalDate::toString).orElse(null);

        // 신호 이벤트 (첫날만)
        List<SignalRecord> events = new ArrayList<>();
        Map<String, List<SignalRecord>> byStock = all.stream().collect(Collectors.groupingBy(SignalRecord::getStockCode, LinkedHashMap::new, Collectors.toList()));
        for (List<SignalRecord> rs : byStock.values()) {
            boolean prev = false;
            for (SignalRecord r : rs) { if (r.isSignal() && !prev) events.add(r); prev = r.isSignal(); }
        }
        Map<LocalDate, List<SignalRecord>> byDay = all.stream().filter(r -> x20(r) != null).collect(Collectors.groupingBy(SignalRecord::getSignalDate));

        List<Row> rows = new ArrayList<>();
        rows.add(row("ALL", "엔진 신호 전체", events, byStock, byDay));
        for (String t : List.of("PULLBACK", "RECLAIM", "BREAKOUT", "KNEE")) {
            List<SignalRecord> ev = events.stream().filter(r -> t.equals(r.getEntryType())).toList();
            if (!ev.isEmpty()) rows.add(row(t, typeLabel(t), ev, byStock, byDay));
        }
        if (nation == Nation.US) {
            List<SignalRecord> ev = events.stream().filter(r -> r.getMomentumPct() != null && r.getMomentumPct() <= 20).toList();
            if (!ev.isEmpty()) rows.add(row("MOM20", "모멘텀 상위 20% 종목의 신호", ev, byStock, byDay));
        }
        // 참고: 신호 아닌 날 전체 (비교군 자체의 수준)
        List<SignalRecord> non = all.stream().filter(r -> !r.isSignal()).toList();
        rows.add(plain("NONSIGNAL", "신호 아닌 날 (비교 기준)", non));

        String note = "가상 추적이에요. 실제 매수 기록이 아니에요. 신호 다음 거래일 시가 체결, 비용 포함(" + (nation == Nation.KR ? "왕복 0.28%" : "왕복 0.35%") + "). "
                + "같은 종목 다른 날 대비 = 진입 타점 효과, 같은 날 전체 대비 = 시장 상승분을 뺀 효과예요.";
        return new Stats(nation.name(), SignalVersion.ENGINE, SignalVersion.TRACK, all.size(), done, first, last, rows, note);
    }

    private Row row(String key, String label, List<SignalRecord> ev, Map<String, List<SignalRecord>> byStock, Map<LocalDate, List<SignalRecord>> byDay) {
        List<SignalRecord> tr = ev.stream().filter(r -> x20(r) != null).toList();
        Map<Long, List<Double>> dStock = new HashMap<>(), dDay = new HashMap<>();
        for (SignalRecord r : tr) {
            long wk = weekKey(r.getSignalDate());
            Double base = byStock.getOrDefault(r.getStockCode(), List.of()).stream()
                    .filter(x -> !x.isSignal() && x20(x) != null && Math.abs(ChronoUnit.DAYS.between(x.getSignalDate(), r.getSignalDate())) <= 90)
                    .mapToDouble(SignalStatsService::x20).boxed().collect(Collectors.collectingAndThen(Collectors.toList(), SignalStatsService::meanOrNull));
            if (base != null) dStock.computeIfAbsent(wk, k -> new ArrayList<>()).add(x20(r) - base);
            Double day = byDay.getOrDefault(r.getSignalDate(), List.of()).stream()
                    .filter(x -> x != r).map(SignalStatsService::x20).collect(Collectors.collectingAndThen(Collectors.toList(), SignalStatsService::meanOrNull));
            if (day != null) dDay.computeIfAbsent(wk, k -> new ArrayList<>()).add(x20(r) - day);
        }
        double[] s = clusterT(dStock), d = clusterT(dDay);
        int weeks = (int) tr.stream().map(r -> weekKey(r.getSignalDate())).distinct().count();
        String status;
        if (tr.size() < MIN_EVENTS || weeks < MIN_WEEKS || Double.isNaN(s[1])) status = "검증 중";
        else if (s[0] > 0 && s[1] >= T_LINE) status = "우위 관찰 (확정 아님)";
        else if (s[0] < 0 && s[1] <= -T_LINE) status = "비교군보다 낮음";
        else status = "차이 확인 안 됨";
        return new Row(key, label, ev.size(), tr.size(), weeks,
                mean(ev, SignalRecord::getRet10), median(ev, SignalRecord::getRet10), mean(ev, SignalRecord::getRet20), median(ev, SignalRecord::getRet20),
                mean(ev, SignalStatsService::x20), median(ev, SignalStatsService::x20), mean(ev, SignalRecord::getMfe20), mean(ev, SignalRecord::getMae20),
                rate(ev, SignalRecord::getStopHit20), rate(ev, SignalRecord::getTargetHit20),
                nan(s[0]), nan(s[1]), nan(d[0]), nan(d[1]), status);
    }

    private Row plain(String key, String label, List<SignalRecord> rs) {
        int tr = (int) rs.stream().filter(r -> x20(r) != null).count();
        return new Row(key, label, rs.size(), tr, 0, mean(rs, SignalRecord::getRet10), median(rs, SignalRecord::getRet10),
                mean(rs, SignalRecord::getRet20), median(rs, SignalRecord::getRet20), mean(rs, SignalStatsService::x20), median(rs, SignalStatsService::x20),
                mean(rs, SignalRecord::getMfe20), mean(rs, SignalRecord::getMae20), null, null, null, null, null, null, "기준");
    }

    /** 20거래일이 다 지난 기록만 쓴다 */
    static Double x20(SignalRecord r) { return r.getRet20() != null ? r.getX20Net() : null; }

    /** 주 단위 묶음: 전체 평균 차이와 t (주 평균들의 표준오차). 주 3개 미만이면 NaN */
    static double[] clusterT(Map<Long, List<Double>> byWeek) {
        List<Double> allv = byWeek.values().stream().flatMap(List::stream).toList();
        if (allv.isEmpty()) return new double[]{Double.NaN, Double.NaN};
        double m = allv.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        List<Double> wm = byWeek.values().stream().map(l -> l.stream().mapToDouble(Double::doubleValue).average().orElse(0)).toList();
        if (wm.size() < 3) return new double[]{m, Double.NaN};
        double mu = wm.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double var = wm.stream().mapToDouble(x -> (x - mu) * (x - mu)).sum() / (wm.size() - 1);
        double se = Math.sqrt(var / wm.size());
        return new double[]{m, se > 0 ? m / se : Double.NaN};
    }

    private static long weekKey(LocalDate d) { return d.get(IsoFields.WEEK_BASED_YEAR) * 100L + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR); }
    private static Double meanOrNull(List<Double> v) { return v.isEmpty() ? null : v.stream().mapToDouble(Double::doubleValue).average().orElse(0); }
    private static Double nan(double v) { return Double.isNaN(v) ? null : v; }
    private static Double mean(List<SignalRecord> rs, Function<SignalRecord, Double> f) {
        List<Double> v = rs.stream().map(f).filter(Objects::nonNull).toList(); return meanOrNull(v);
    }
    private static Double median(List<SignalRecord> rs, Function<SignalRecord, Double> f) {
        List<Double> v = rs.stream().map(f).filter(Objects::nonNull).sorted().toList();
        if (v.isEmpty()) return null; int n = v.size(); return n % 2 == 1 ? v.get(n / 2) : (v.get(n / 2 - 1) + v.get(n / 2)) / 2;
    }
    private static Double rate(List<SignalRecord> rs, Function<SignalRecord, Boolean> f) {
        List<Boolean> v = rs.stream().map(f).filter(Objects::nonNull).toList();
        return v.isEmpty() ? null : v.stream().filter(Boolean::booleanValue).count() / (double) v.size();
    }
    private static String typeLabel(String t) {
        return switch (t) { case "PULLBACK" -> "눌림목 신호"; case "RECLAIM" -> "탈환 신호"; case "BREAKOUT" -> "돌파 신호"; case "KNEE" -> "무릎 신호"; default -> t; };
    }
}
