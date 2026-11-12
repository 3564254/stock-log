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
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** 보유 종목끼리 얼마나 같이 움직이는지(상관), 시장 민감도(베타), 변동성. 포트폴리오 점검 카드용 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioCheckService {

    private static final int DAYS = 120;
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final String KR_INDEX = "069500", US_INDEX = "SPY";

    private final StockChartService chartService;

    public record Pair(String a, String b, double corr) {}
    public record Beta(String code, double beta) {}
    /** days: 계산에 쓴 공통 거래일 수 (0이면 계산 불가) */
    public record Check(int days, int n, double avgCorr, double effN, double volAnnual, double beta,
                        List<Pair> pairs, List<Beta> betas, List<String> missing) {}

    private record Cached(Check v, Instant at) {}
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    /** weights: 종목코드 → 비중. 비중을 모르면 1로 넣어 동일 비중 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Check check(Map<String, Double> weights) {
        if (weights == null || weights.isEmpty()) return new Check(0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of());
        String key = new TreeMap<>(weights).toString();
        Cached c = cache.get(key);
        if (c != null && c.at().plus(TTL).isAfter(Instant.now())) return c.v();
        Check v = compute(weights);
        cache.put(key, new Cached(v, Instant.now()));
        return v;
    }

    private Check compute(Map<String, Double> weights) {
        Map<String, Map<LocalDate, Double>> rets = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (String code : weights.keySet()) {
            Map<LocalDate, Double> r = returns(code);
            if (r.size() >= 30) rets.put(code, r); else missing.add(code);
        }
        Map<LocalDate, Double> kr = returns(KR_INDEX), us = returns(US_INDEX);

        List<String> codes = new ArrayList<>(rets.keySet());
        int n = codes.size();
        if (n == 0) return new Check(0, 0, 0, 0, 0, 0, List.of(), List.of(), missing);

        // 공통 날짜 (최근 DAYS일)
        TreeSet<LocalDate> common = new TreeSet<>(rets.get(codes.get(0)).keySet());
        for (String cd : codes) common.retainAll(rets.get(cd).keySet());
        List<LocalDate> dates = new ArrayList<>(common);
        if (dates.size() > DAYS) dates = dates.subList(dates.size() - DAYS, dates.size());
        int T = dates.size();
        if (T < 30) return new Check(T, n, 0, 0, 0, 0, List.of(), List.of(), missing);

        double[][] x = new double[n][T];
        for (int i = 0; i < n; i++) for (int t = 0; t < T; t++) x[i][t] = rets.get(codes.get(i)).get(dates.get(t));
        // 한국 · 미국 종목이 섞이면 같은 날짜의 수익률이 시차 때문에 어긋나 상관이 낮게 나온다
        boolean hasKr = codes.stream().anyMatch(c -> c.matches("\\d+")), hasUs = codes.stream().anyMatch(c -> !c.matches("\\d+"));
        int span = hasKr && hasUs ? 2 : 1;
        if (span == 2) {
            double[][] y = new double[n][T - 1];
            for (int i = 0; i < n; i++) for (int t = 1; t < T; t++) y[i][t - 1] = x[i][t] + x[i][t - 1];
            x = y; T = T - 1;
        }
        double[] mean = new double[n], sd = new double[n];
        for (int i = 0; i < n; i++) {
            double m = 0; for (double v : x[i]) m += v; m /= T; mean[i] = m;
            double s = 0; for (double v : x[i]) s += (v - m) * (v - m); sd[i] = Math.sqrt(s / (T - 1));
        }
        double[][] cov = new double[n][n];
        for (int i = 0; i < n; i++) for (int j = i; j < n; j++) {
            double s = 0; for (int t = 0; t < T; t++) s += (x[i][t] - mean[i]) * (x[j][t] - mean[j]);
            cov[i][j] = cov[j][i] = s / (T - 1);
        }
        double wsum = 0; double[] w = new double[n];
        for (int i = 0; i < n; i++) { w[i] = Math.max(0, weights.getOrDefault(codes.get(i), 1.0)); wsum += w[i]; }
        if (wsum <= 0) { Arrays.fill(w, 1.0 / n); } else for (int i = 0; i < n; i++) w[i] /= wsum;

        double varP = 0, wsig = 0;
        for (int i = 0; i < n; i++) { wsig += w[i] * sd[i]; for (int j = 0; j < n; j++) varP += w[i] * w[j] * cov[i][j]; }
        double effN = varP > 0 ? Math.min(n, wsig * wsig / varP) : n;

        List<Pair> pairs = new ArrayList<>(); double csum = 0; int cnt = 0;
        for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) {
            double r = sd[i] > 0 && sd[j] > 0 ? cov[i][j] / (sd[i] * sd[j]) : 0;
            pairs.add(new Pair(codes.get(i), codes.get(j), round(r))); csum += r; cnt++;
        }
        pairs.sort(Comparator.comparingDouble(Pair::corr).reversed());

        List<Beta> betas = new ArrayList<>(); double betaP = 0;
        for (int i = 0; i < n; i++) {
            Map<LocalDate, Double> idx = codes.get(i).matches("\\d+") ? kr : us;
            double b = beta(rets.get(codes.get(i)), idx);
            betas.add(new Beta(codes.get(i), round(b))); betaP += w[i] * b;
        }
        return new Check(T, n, cnt > 0 ? round(csum / cnt) : 0, round(effN), round(Math.sqrt(varP) * Math.sqrt(252.0 / span) * 100),
                round(betaP), pairs.subList(0, Math.min(5, pairs.size())), betas, missing);
    }

    /** 최근 DAYS일 종목 수익률과 지수 수익률이 겹치는 날로 베타 = cov / var(지수) */
    private static double beta(Map<LocalDate, Double> s, Map<LocalDate, Double> idx) {
        List<LocalDate> d = new ArrayList<>(new TreeSet<>(s.keySet()));
        d.retainAll(idx.keySet());
        if (d.size() > DAYS) d = d.subList(d.size() - DAYS, d.size());
        if (d.size() < 30) return 0;
        double ms = 0, mi = 0; for (LocalDate k : d) { ms += s.get(k); mi += idx.get(k); } ms /= d.size(); mi /= d.size();
        double cv = 0, vi = 0; for (LocalDate k : d) { cv += (s.get(k) - ms) * (idx.get(k) - mi); vi += (idx.get(k) - mi) * (idx.get(k) - mi); }
        return vi > 0 ? cv / vi : 0;
    }

    private Map<LocalDate, Double> returns(String code) {
        Map<LocalDate, Double> out = new HashMap<>();
        try {
            List<StockChartDto> d = chartService.getDailyChartDtoAsc(code);
            for (int i = 1; i < d.size(); i++) {
                double p0 = d.get(i - 1).close().doubleValue(), p1 = d.get(i).close().doubleValue();
                if (p0 > 0 && p1 > 0 && d.get(i).date() != null) out.put(d.get(i).date(), p1 / p0 - 1);
            }
        } catch (Exception e) {
            log.warn("[포트폴리오 점검] {} 일봉 조회 실패: {}", code, e.getMessage());
        }
        return out;
    }

    private static double round(double v) { return Math.round(v * 100) / 100.0; }
}
