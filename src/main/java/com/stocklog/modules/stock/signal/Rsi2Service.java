package com.stocklog.modules.stock.signal;

import com.stocklog.modules.common.type.Period;
import com.stocklog.modules.stock.chart.StockChart;
import com.stocklog.modules.stock.chart.StockChartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** RSI2 눌림 */
@Service
@RequiredArgsConstructor
public class Rsi2Service {

    private final StockChartRepository chartRepository;
    private final com.stocklog.modules.market.index.repository.MarketIndexHistoryRepository indexHistoryRepository;

    /** RSI2 계산 결과 */
    public record Rsi2(Double rsi2, Boolean up, String basis, boolean dip, Double close, Double sma5, boolean aboveSma5, LocalDate asOf,
                       Double vix, boolean fear, Double vixAvg10, boolean spike, boolean rally) {}

    private volatile Double vixCache, vixAvgCache; private volatile long vixAt;

    /** 최근 VIX 종가 */
    private Double vix() {
        long now = System.currentTimeMillis();
        if (now - vixAt < 600_000) return vixCache;
        Double v = null, a = null;
        try {
            var h = indexHistoryRepository.findHistoryDESCTopN("VIXCLS", 11);   // 최신순
            if (!h.isEmpty() && h.get(0).getCloseValue() != null) v = h.get(0).getCloseValue().doubleValue();
            if (h.size() >= 11) {
                double s = 0; int n = 0;
                for (int i = 1; i < 11; i++) if (h.get(i).getCloseValue() != null) { s += h.get(i).getCloseValue().doubleValue(); n++; }
                if (n == 10) a = s / 10;
            }
        } catch (Exception ignored) { }
        vixCache = v; vixAvgCache = a; vixAt = now;
        return v;
    }

    public Rsi2 of(String code) {
        List<StockChart> day = chartRepository.findByCodeAndPeriodOrderByTradeDateAsc(code, Period.DAY);
        double[] c = day.stream().map(StockChart::getClosePrice).filter(v -> v != null && v.signum() > 0).mapToDouble(BigDecimal::doubleValue).toArray();
        if (c.length < 10) return null;
        double close = c[c.length - 1];
        Double r = rsi2(c);
        Boolean up = null; String basis = null;
        if (c.length >= 200) {
            up = close > avg(c, 200); basis = "200일선";
        } else {
            List<StockChart> wk = chartRepository.findByCodeAndPeriodOrderByTradeDateAsc(code, Period.WEEK);
            double[] w = wk.stream().map(StockChart::getClosePrice).filter(v -> v != null && v.signum() > 0).mapToDouble(BigDecimal::doubleValue).toArray();
            if (w.length >= 40) { up = w[w.length - 1] > avg(w, 40); basis = "40주선"; }
        }
        double s5 = avg(c, 5);
        boolean dip = Boolean.TRUE.equals(up) && r != null && r < 10;
        Double vx = vix(), va = vixAvgCache;
        boolean fear = dip && vx != null && vx >= 20;
        return new Rsi2(r, up, basis, dip, close, s5, close > s5, day.get(day.size() - 1).getTradeDate(), vx, fear,
                va, fear && va != null && vx >= va * 1.1, Boolean.FALSE.equals(up) && r != null && r > 90);
    }

    /** Wilder 2일 RSI (시험 코드와 같은 식: 처음 두 변화의 평균에서 시작, 이후 (이전 + 이번)/2) */
    static Double rsi2(double[] c) {
        double ag = 0, al = 0;
        for (int i = 1; i < c.length; i++) {
            double ch = c[i] - c[i - 1], g = Math.max(ch, 0), l = Math.max(-ch, 0);
            if (i <= 2) { ag += g / 2; al += l / 2; } else { ag = (ag + g) / 2; al = (al + l) / 2; }
        }
        return al == 0 ? 100.0 : 100 - 100 / (1 + ag / al);
    }

    private static double avg(double[] a, int n) {
        double s = 0; for (int i = a.length - n; i < a.length; i++) s += a[i]; return s / n;
    }
}
