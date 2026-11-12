package com.stocklog.modules.stock.signal;

import java.time.LocalDate;
import java.util.List;

/** 신호 이후 결과 계산. DB·외부 호출 없는 순수 계산이라 테스트로 고정한다 */
public final class SignalOutcomes {
    private SignalOutcomes() {}

    public record Bar(LocalDate date, double open, double high, double low, double close) {}

    public record Outcome(String status, LocalDate entryDate, Double entryOpen,
                          Double ret5, Double ret10, Double ret20, Double mfe20, Double mae20,
                          Boolean stopHit20, Boolean targetHit20, String firstHit,
                          Double x10Net, Double x20Net, Double trailNet, Integer trailDays) {}

    static final int TRAIL_MAX = 120;
    static final double STOP_ATR = 2.0, TRAIL_ATR = 3.0;

    public static double costOf(String code) {
        return code != null && code.matches("\\d+") ? 0.0028 : 0.0035;
    }

    public static Outcome compute(List<Bar> bars, LocalDate signalDate, double atr, double planStop, double planTarget, double cost) {
        int e = -1;
        for (int i = 0; i < bars.size(); i++) if (bars.get(i).date().isAfter(signalDate)) { e = i; break; }
        if (e < 0) return new Outcome("PENDING", null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        Bar eb = bars.get(e);
        double px = eb.open();
        if (!(px > 0)) return new Outcome("NODATA", eb.date(), null, null, null, null, null, null, null, null, null, null, null, null, null);
        int avail = bars.size() - e;   // 체결일 포함 이후 봉 수

        Double r5 = avail >= 5 ? bars.get(e + 4).close() / px - 1 : null;
        Double r10 = avail >= 10 ? bars.get(e + 9).close() / px - 1 : null;
        Double r20 = avail >= 20 ? bars.get(e + 19).close() / px - 1 : null;

        Double mfe = null, mae = null; Boolean sh = null, th = null; String first = null;
        if (avail >= 20) {
            double hi = 0, lo = 0; boolean s = false, t = false; first = "NONE";
            for (int j = e; j < e + 20; j++) {
                Bar b = bars.get(j);
                hi = Math.max(hi, b.high() / px - 1); lo = Math.min(lo, b.low() / px - 1);
                boolean sj = planStop > 0 && b.low() <= planStop, tj = planTarget > 0 && b.high() >= planTarget;
                if ("NONE".equals(first)) {
                    if (sj) first = "STOP";            // 같은 날 둘 다면 보수적으로 손절 먼저
                    else if (tj) first = "TARGET";
                }
                s |= sj; t |= tj;
            }
            mfe = hi; mae = lo; sh = s; th = t;
        }

        Double x10 = fixedExit(bars, e, px, atr, 10, cost);
        Double x20 = fixedExit(bars, e, px, atr, 20, cost);

        // 추적 손절
        Double trail = null; Integer tdays = null;
        double stop0 = planStop > 0 && planStop < px ? planStop : px - STOP_ATR * atr;
        double hiC = eb.close();
        for (int j = e; j < bars.size(); j++) {
            Bar b = bars.get(j);
            hiC = Math.max(hiC, b.close());
            double a = atrAt(bars, j, atr);
            double line = Math.max(stop0, hiC - TRAIL_ATR * a);
            if (j - e + 1 >= TRAIL_MAX) { trail = b.close() / px * (1 - cost) - 1; tdays = j - e + 1; break; }
            if (b.close() < line) {
                if (j + 1 < bars.size()) { trail = bars.get(j + 1).open() / px * (1 - cost) - 1; tdays = j + 1 - e; }
                break;   // 다음날 시가가 아직 없으면 다음 평가 때
            }
        }

        boolean done = r20 != null && x20 != null && trail != null;
        String status = done ? "DONE" : (r5 != null ? "PARTIAL" : "PENDING");
        return new Outcome(status, eb.date(), px, r5, r10, r20, mfe, mae, sh, th, first, x10, x20, trail, tdays);
    }

    /** 보호 손절 또는 H거래일째 다음날 시가. 아직 못 정하면 null */
    static Double fixedExit(List<Bar> bars, int e, double px, double atr, int h, double cost) {
        double stop = px - STOP_ATR * atr;
        for (int j = e; j < bars.size(); j++) {
            if (bars.get(j).close() < stop || j == e + h - 1) {
                return j + 1 < bars.size() ? bars.get(j + 1).open() / px * (1 - cost) - 1 : null;
            }
        }
        return null;
    }

    /** 그날까지 14개 봉의 단순 평균 TR. 봉이 모자라면 신호일 ATR */
    static double atrAt(List<Bar> bars, int j, double fallback) {
        if (j < 14) return fallback;
        double s = 0;
        for (int k = j - 13; k <= j; k++) {
            Bar b = bars.get(k), p = bars.get(k - 1);
            s += Math.max(b.high() - b.low(), Math.max(Math.abs(b.high() - p.close()), Math.abs(b.low() - p.close())));
        }
        return s / 14;
    }
}
