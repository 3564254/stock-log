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
    /**
     * 위쪽에 유의미한 저항 구간이 없을 때(신고가 돌파 등) 손익비를 가늠할 기본 목표 폭.
     * 화면의 목표가로는 쓰지 않고, '손익비 검사를 건너뛰어 진입 가능으로 판정'되는 것만 막는다.
     */
    private static final double FALLBACK_TARGET_ATR = 3.0;

    public static Verdict judge(EntryType type, SignalInputs in, EntrySetup setup) {
        if (in.price() > setup.entry().high()) {
            return type == EntryType.PULLBACK ? Verdict.WAIT_FOR_ZONE : Verdict.CHASE_RISK;
        }
        double rr = setup.rr();
        if (!Double.isFinite(rr)) {
            // 목표 저항이 없으면 예전엔 손익비 검사 없이 통과했다. ATR 기준 목표로 손익비를 추정한다
            double risk = setup.refPrice() - setup.stop();
            rr = risk > 0 ? (FALLBACK_TARGET_ATR * in.atr()) / risk : Double.NaN;
        }
        if (!Double.isFinite(rr) || rr < MIN_RR) {
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

    /** 보유 기준 손절선과 그 근거 (예: "지지 구간 하단 (MA20·POC)") */
    public record HoldingStop(double price, String basis) {
        public static final HoldingStop NONE = new HoldingStop(0.0, "");
    }

    /** 현재가에 이보다 가까운 손절선은 일상적인 흔들림에도 걸리므로 건너뛴다 */
    private static final double HOLD_MIN_GAP_ATR = 0.5;
    /** 지지 구간도 최근 저점도 쓸 수 없을 때의 기본 폭 */
    private static final double HOLD_FALLBACK_ATR = 2.0;

    /**
     * 보유 관점의 손절선(이탈 기준선). 차트 국면과 상관없이 ATR만 있으면 항상 값을 낸다.
     * 예전에는 강한 보유·진입 준비 같은 좋은 국면에서만 계산해서, 손절선이 가장 필요한 하락 국면에 비어 있었다.
     *
     * 1. 현재가 아래 유의미한 지지 구간 하단 - 버퍼 (가까운 구간부터, 현재가에 너무 붙은 건 건너뜀)
     * 2. 없으면 최근 저점 - 버퍼
     * 3. 그것도 안 되면 현재가 - 2 ATR
     *
     * 무상태라서, 보유 중 손절선을 올리기만 하려면 호출하는 쪽이 이전 값과 max()를 취해야 한다.
     */
    public static HoldingStop holdingStop(SignalInputs in, List<PriceZone> zones) {
        double price = in.price();
        double atr = in.atr();
        if (!(price > 0) || !(atr > 0)) return HoldingStop.NONE;
        double ceiling = price - HOLD_MIN_GAP_ATR * atr;

        Optional<HoldingStop> bySupport = zones.stream()
                .filter(z -> z.strength() >= MIN_SUPPORT_STRENGTH && z.high() < price)
                .sorted(Comparator.comparingDouble(PriceZone::high).reversed())
                .map(z -> new HoldingStop(z.low() - STOP_BUFFER_ATR * atr,
                        "지지 구간 하단 (" + String.join("·", z.sources()) + ")"))
                .filter(h -> h.price() > 0 && h.price() <= ceiling)
                .findFirst();
        if (bySupport.isPresent()) return bySupport.get();

        double low = in.recentLow();
        if (Double.isFinite(low) && low < price) {
            double stop = low - STOP_BUFFER_ATR * atr;
            if (stop > 0 && stop <= ceiling) return new HoldingStop(stop, "최근 저점 아래");
        }

        double stop = price - HOLD_FALLBACK_ATR * atr;
        return stop > 0 ? new HoldingStop(stop, "변동폭(ATR) 2배 아래") : HoldingStop.NONE;
    }

    /** 보유 관점의 익절 목표: 현재가 위 가장 가까운 유의미한 저항 구간 하단과 그 근거 */
    public record HoldingTarget(double price, String basis) {
        public static final HoldingTarget NONE = new HoldingTarget(0.0, "");
    }

    /**
     * 보유 관점의 익절 목표. 현재가보다 최소 0.5 ATR 위에 있는 가장 가까운 유의미한 저항 구간의 하단.
     * 위쪽에 저항이 없으면(신고가 구간 등) 목표를 지어내지 않고 NONE을 돌려준다.
     */
    public static HoldingTarget holdingTarget(SignalInputs in, List<PriceZone> zones) {
        double price = in.price();
        double atr = in.atr();
        if (!(price > 0) || !(atr > 0)) return HoldingTarget.NONE;
        double floor = price + HOLD_MIN_GAP_ATR * atr;
        return zones.stream()
                .filter(z -> z.strength() >= MIN_TARGET_STRENGTH && z.low() >= floor)
                .min(Comparator.comparingDouble(PriceZone::low))
                .map(z -> new HoldingTarget(z.low(), "저항 구간 하단 (" + String.join("·", z.sources()) + ")"))
                .orElse(HoldingTarget.NONE);
    }
}
