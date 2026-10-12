package com.stocklog.modules.stock.analysis.signal.trading;

import com.stocklog.modules.common.type.ChartPhase;
import com.stocklog.modules.stock.analysis.signal.level.PriceZone;
import com.stocklog.modules.stock.analysis.signal.level.SignalInputs;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.stocklog.modules.common.finance.indicator.phase.PhaseConst.*;

/**
 * 진입 계획 수립: 진입 유형별 구간 + 구조적 손절 -> 목표 -> RR -> 판정(Verdict).
 * 상태가 없는 순수 계산이라 전부 static이다. ATR은 구간 폭, 손절 버퍼, 추격 여부에만 쓴다.
 */
public final class EntryPlanner {

    private EntryPlanner() {}

    /** 신규 진입 계획을 세울 수 있는 Phase. STRONG_HOLD는 보유 관점이라 제외한다. */
    private static final Set<ChartPhase> PLANNABLE = EnumSet.of(
            ChartPhase.ENTRY_READY, ChartPhase.RE_ENTRY,
            ChartPhase.COOLDOWN, ChartPhase.RECOVERY_WATCH);

    /** 정식 타점이 없어도 참고 가격대를 보여줄 Phase. 하락·손절 계열은 일부러 제외한다. */
    private static final Set<ChartPhase> REFERENCE_PHASES = EnumSet.of(
            ChartPhase.WATCH, ChartPhase.SETUP, ChartPhase.ENTRY_READY, ChartPhase.RE_ENTRY,
            ChartPhase.COOLDOWN, ChartPhase.STRONG_HOLD, ChartPhase.RECOVERY_WATCH,
            ChartPhase.WARNING_ZONE, ChartPhase.SCALE_OUT, ChartPhase.CLIMAX);

    public static boolean isPlannablePhase(ChartPhase phase) {
        return PLANNABLE.contains(phase);
    }

    public static boolean isReferencePhase(ChartPhase phase) {
        return REFERENCE_PHASES.contains(phase);
    }

    /** NONE, INSUFFICIENT_DATA는 계획 대상이 아니다. */
    public static boolean isPlannableType(EntryType type) {
        return type == EntryType.PULLBACK || type == EntryType.BREAKOUT || type == EntryType.RECLAIM;
    }

    // ------------------------------------------------------------------
    // 진입 유형별 진입 구간 + 구조적 손절
    // ------------------------------------------------------------------
    private record EntryStop(PriceZone zone, double stop) {}

    public static Optional<EntrySetup> build(EntryType type, SignalInputs in, List<PriceZone> zones) {
        Optional<EntryStop> es = switch (type) {
            case PULLBACK -> pullbackEntry(in, zones);
            case BREAKOUT -> breakoutEntry(in);
            case RECLAIM  -> reclaimEntry(in);
            case NONE, INSUFFICIENT_DATA -> Optional.empty();
        };
        return es.flatMap(x -> finish(type, x.zone(), x.stop(), in, zones));
    }

    /** 진입 구간과 손절이 정해진 뒤 기준가, 목표, RR을 채워 계획을 완성한다. */
    private static Optional<EntrySetup> finish(EntryType type, PriceZone entry, double stop,
                                               SignalInputs in, List<PriceZone> zones) {
        // 구간 안이면 현재가, 구간 위면 구간 상단(보수적 체결 가정)을 기준가로 쓴다
        double ref = Math.min(in.price(), entry.high());
        double risk = ref - stop;
        if (risk <= 0) return Optional.empty();

        // 목표: 진입 구간과 "현재가" 둘 다보다 위에 있는 가장 가까운 유의미한 저항 구간의 하단. 없으면 NaN.
        // (현재가가 이미 구간 위로 올라와 있으면, 지나온 가격대는 목표가 될 수 없다)
        double floor = Math.max(entry.high(), in.price());
        double[] targets = zones.stream()
                .filter(z -> z.low() > floor && z.strength() >= MIN_TARGET_STRENGTH)
                .mapToDouble(PriceZone::low)
                .sorted().limit(2).toArray();
        double target1 = targets.length > 0 ? targets[0] : Double.NaN;
        double target2 = targets.length > 1 ? targets[1] : Double.NaN;
        double rr = Double.isFinite(target1) ? (target1 - ref) / risk : Double.NaN;

        return Optional.of(new EntrySetup(type, entry, ref, stop, target1, target2, rr));
    }

    /**
     * 참고 가격대: 정식 진입 계획(PULLBACK/BREAKOUT/RECLAIM)이 없어도,
     * 현재가 아래 가장 가까운 유의미한 지지 구간을 "관심 가격대"로 보여준다.
     * type은 NONE이라 화면에서 정식 타점과 구분되고, 비중은 0이다.
     */
    public static Optional<EntrySetup> reference(SignalInputs in, List<PriceZone> zones) {
        return zones.stream()
                .filter(z -> z.strength() >= MIN_SUPPORT_STRENGTH && z.high() < in.price())
                .max(Comparator.comparingDouble(PriceZone::high))
                .flatMap(z -> finish(EntryType.NONE, z, z.low() - STOP_BUFFER_ATR * in.atr(), in, zones));
    }

    private static Optional<EntryStop> pullbackEntry(SignalInputs in, List<PriceZone> zones) {
        return zones.stream()
                .filter(z -> z.strength() >= MIN_SUPPORT_STRENGTH)
                .filter(z -> z.low() <= in.price())
                .filter(z -> (in.price() - z.high()) / in.atr() <= ENTRY_MAX_DIST_ATR)
                .max(Comparator.comparingDouble(PriceZone::high))
                .map(z -> new EntryStop(z, z.low() - STOP_BUFFER_ATR * in.atr()));
    }

    private static Optional<EntryStop> breakoutEntry(SignalInputs in) {
        double rh = in.recentHigh();
        if (!Double.isFinite(rh) || in.price() < rh) return Optional.empty();
        PriceZone z = new PriceZone(rh, rh + ENTRY_BAND_ATR * in.atr(), 0, List.of("최근고점 돌파"));
        return Optional.of(new EntryStop(z, rh - STOP_BUFFER_ATR * in.atr()));
    }

    private static Optional<EntryStop> reclaimEntry(SignalInputs in) {
        double level = (Double.isFinite(in.pivotP()) && in.pivotP() > 0) ? in.pivotP() : in.ma20();
        if (!Double.isFinite(level) || in.price() < level) return Optional.empty();
        PriceZone z = new PriceZone(level, level + ENTRY_BAND_ATR * in.atr(), 0, List.of("회복 레벨"));
        double stop = (Double.isFinite(in.recentLow()) && in.recentLow() < level)
                ? in.recentLow() - STOP_BUFFER_ATR * in.atr()
                : level - in.atr();
        return Optional.of(new EntryStop(z, stop));
    }

    // ------------------------------------------------------------------
    // 판정 + 트리거 + RR(마지막 단계)
    // ------------------------------------------------------------------
    public static Verdict judge(EntryType type, SignalInputs in, EntrySetup setup) {
        if (in.price() > setup.entry().high()) {
            return type == EntryType.PULLBACK ? Verdict.WAIT_FOR_ZONE : Verdict.CHASE_RISK;
        }
        if (Double.isFinite(setup.rr()) && setup.rr() < MIN_RR) {
            return Verdict.RR_TOO_LOW;
        }
        if (!bullishTrigger(in)) {
            return Verdict.WAIT_TRIGGER;
        }
        return Verdict.ACTIONABLE;
    }

    /** 시가를 아직 모르면(NaN) 트리거를 막지 않는다. 시가가 들어오면 양봉을 요구한다. */
    private static boolean bullishTrigger(SignalInputs in) {
        if (!Double.isFinite(in.open())) return true;
        return in.price() > in.open();
    }

    /**
     * 보유 관점의 추적 손절: 현재가 아래 가장 가까운 유의미한 지지 구간 하단 - 버퍼.
     * 이 함수는 무상태라서, 호출하는 쪽이 이전 값과 max()를 취해 아래로 내려가지 않게 해야 한다.
     */
    public static double trailingStop(ChartPhase phase, SignalInputs in, List<PriceZone> zones) {
        if (phase != ChartPhase.STRONG_HOLD && !PLANNABLE.contains(phase)) return 0.0;
        return zones.stream()
                .filter(z -> z.strength() >= MIN_SUPPORT_STRENGTH && z.high() < in.price())
                .max(Comparator.comparingDouble(PriceZone::high))
                .map(z -> z.low() - STOP_BUFFER_ATR * in.atr())
                .orElse(0.0);
    }
}
