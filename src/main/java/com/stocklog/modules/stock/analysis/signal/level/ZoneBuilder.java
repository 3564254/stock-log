package com.stocklog.modules.stock.analysis.signal.level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.stocklog.modules.common.finance.indicator.phase.PhaseConst.*;

/** 가격 레벨(이평, VWAP, POC, 피봇 등)을 ATR 기준으로 묶어 가격 구간(PriceZone)으로 만든다. */
public final class ZoneBuilder {

    private ZoneBuilder() {}

    /** atr은 0보다 큰 유한값이어야 한다(호출하는 쪽에서 검사). */
    public static List<PriceZone> build(SignalInputs in) {
        return cluster(levels(in), in.atr());
    }

    // ------------------------------------------------------------------
    // 가격 레벨 -> 구간(Zone)
    // ------------------------------------------------------------------
    private record Level(String source, double price, double weight) {}

    private static List<Level> levels(SignalInputs in) {
        List<Level> l = new ArrayList<>();
        add(l, "MA20", in.ma20(), 2.0);
        add(l, "MA50", in.ma50(), 2.0);
        add(l, "VWAP20", in.vwap20(), 1.5);
        add(l, "POC", in.poc(), 2.0);
        add(l, "최근저점", in.recentLow(), 2.0);
        add(l, "최근고점", in.recentHigh(), 2.0);
        add(l, "피봇P", in.pivotP(), 1.0);
        add(l, "S1", in.s1(), 1.0);
        add(l, "S2", in.s2(), 1.0);
        add(l, "R1", in.r1(), 1.0);
        add(l, "볼린저하단", in.bbLower(), 1.0);
        add(l, "볼린저상단", in.bbUpper(), 1.0);
        return l;
    }

    private static void add(List<Level> list, String source, double price, double weight) {
        if (Double.isFinite(price) && price > 0) {
            list.add(new Level(source, price, weight));
        }
    }

    /**
     * 구간 최대 폭(ATR 배수). 인접 레벨끼리 이어 붙이는 방식이라 레벨이 촘촘하면 구간이 끝없이 늘어날 수 있다.
     * 구간이 넓어지면 손절(구간 하단 - 버퍼)이 멀어져 손익비가 나빠지므로 이 폭을 넘으면 새 구간으로 끊는다.
     */
    private static final double MAX_ZONE_WIDTH_ATR = 2.0;

    /** ATR 이내로 붙어 있는 레벨을 하나의 구간으로 묶는다. (연쇄 클러스터링: 인접 레벨 기준, 최대 폭 제한) */
    private static List<PriceZone> cluster(List<Level> levels, double atr) {
        List<Level> sorted = levels.stream()
                .sorted(Comparator.comparingDouble(Level::price)).toList();
        List<PriceZone> zones = new ArrayList<>();
        List<Level> group = new ArrayList<>();
        for (Level lv : sorted) {
            if (!group.isEmpty()
                    && (lv.price() - group.get(group.size() - 1).price() > atr * CLUSTER_ATR
                        || lv.price() - group.get(0).price() > atr * MAX_ZONE_WIDTH_ATR)) {
                zones.add(toZone(group, atr));
                group = new ArrayList<>();
            }
            group.add(lv);
        }
        if (!group.isEmpty()) zones.add(toZone(group, atr));
        return zones;
    }

    private static PriceZone toZone(List<Level> group, double atr) {
        double lo = group.get(0).price();
        double hi = group.get(group.size() - 1).price();
        double minWidth = atr * MIN_ZONE_WIDTH_ATR;
        if (hi - lo < minWidth) {
            double mid = (hi + lo) / 2;
            lo = mid - minWidth / 2;
            hi = mid + minWidth / 2;
        }
        double strength = group.stream().mapToDouble(Level::weight).sum();
        return new PriceZone(lo, hi, strength, group.stream().map(Level::source).toList());
    }
}
