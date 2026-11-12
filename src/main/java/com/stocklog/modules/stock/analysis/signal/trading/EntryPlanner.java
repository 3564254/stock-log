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

/** 진입 계획 수립: 진입 유형별 구간 + 구조적 손절 -> 목표 -> RR -> 판정(Verdict) */
public final class EntryPlanner {

    private EntryPlanner() {}

    /** 신규 진입 계획을 세울 수 있는 Phase */
    private static final Set<ChartPhase> PLANNABLE = EnumSet.of(
            ChartPhase.ENTRY_READY, ChartPhase.RE_ENTRY,
            ChartPhase.COOLDOWN, ChartPhase.RECOVERY_WATCH,
            ChartPhase.STRONG_HOLD, ChartPhase.SETUP);

    /** 정식 타점이 없어도 참고 가격대를 보여줄 Phase. 하락·손절 계열은 일부러 제외한다 */
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

    /** 계획을 시도할 진입 유형 순서. 앞에서부터 build()가 성공하는 첫 유형을 쓴다 */
    public static List<EntryType> candidateTypes(ChartPhase phase, EntryType classified) {
        if (!isPlannablePhase(phase)) return List.of();
        List<EntryType> defaults = switch (phase) {
            case ENTRY_READY -> List.of(EntryType.BREAKOUT, EntryType.PULLBACK);
            case RECOVERY_WATCH -> List.of(EntryType.RECLAIM, EntryType.KNEE); // 피봇 탈환이 안 되면(아직 아래면) 무릎
            case RE_ENTRY, COOLDOWN, STRONG_HOLD, SETUP -> List.of(EntryType.PULLBACK);
            default -> List.of();
        };
        java.util.LinkedHashSet<EntryType> order = new java.util.LinkedHashSet<>();
        if (isPlannableType(classified)) order.add(classified);
        order.addAll(defaults);
        return List.copyOf(order);
    }

    /** NONE, INSUFFICIENT_DATA는 계획 대상이 아니다 */
    public static boolean isPlannableType(EntryType type) {
        return type == EntryType.PULLBACK || type == EntryType.BREAKOUT || type == EntryType.RECLAIM
                || type == EntryType.KNEE;
    }

    // 진입 유형별 진입 구간 + 구조적 손절
    private record EntryStop(PriceZone zone, double stop) {}

    public static Optional<EntrySetup> build(EntryType type, SignalInputs in, List<PriceZone> zones) {
        Optional<EntryStop> es = switch (type) {
            case PULLBACK -> pullbackEntry(in, zones);
            case BREAKOUT -> breakoutEntry(in);
            case RECLAIM  -> reclaimEntry(in);
            case KNEE     -> kneeEntry(in);
            case NONE, INSUFFICIENT_DATA -> Optional.empty();
        };
        return es.flatMap(x -> finish(type, x.zone(), x.stop(), in, zones));
    }

    /** 진입 구간과 손절이 정해진 뒤 기준가, 목표, RR을 채워 계획을 완성한다 */
    private static Optional<EntrySetup> finish(EntryType type, PriceZone entry, double stop,
                                               SignalInputs in, List<PriceZone> zones) {
        // 구간 안이면 현재가, 구간 위면 구간 상단(보수적 체결 가정)을 기준가로 쓴다
        double ref = Math.max(entry.low(), Math.min(in.price(), entry.high()));
        double risk = ref - stop;
        if (risk <= 0) return Optional.empty();

        // 목표: 진입 구간과 "현재가" 둘 다보다 위에 있는 가장 가까운 유의미한 저항 구간의 하단. 없으면 NaN
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

    /** 참고 가격대: 정식 진입 계획이 없어도, 현재가 아래 가장 가까운 유의미한 지지 구간을 "관심 가격대"로 보여준다 */
    public static Optional<EntrySetup> reference(SignalInputs in, List<PriceZone> zones) {
        return zones.stream()
                .filter(z -> z.strength() >= MIN_SUPPORT_STRENGTH && z.high() < in.price())
                .max(Comparator.comparingDouble(PriceZone::high))
                .flatMap(z -> finish(EntryType.NONE, z, z.low() - STOP_BUFFER_ATR * in.atr(), in, zones));
    }

    private static Optional<EntryStop> pullbackEntry(SignalInputs in, List<PriceZone> zones) {
        return zones.stream()
                .filter(z -> z.strength() >= MIN_SUPPORT_STRENGTH)
                .filter(z -> z.low() <= in.price() || isBoundaryChop(in, z.low()))
                .filter(z -> (in.price() - z.high()) / in.atr() <= ENTRY_MAX_DIST_ATR)
                .max(Comparator.comparingDouble(PriceZone::high))
                .map(z -> new EntryStop(z, z.low() - STOP_BUFFER_ATR * in.atr()));
    }

    private static Optional<EntryStop> breakoutEntry(SignalInputs in) {
        double rh = in.recentHigh();
        if (!Double.isFinite(rh) || (in.price() < rh && !isBoundaryChop(in, rh))) return Optional.empty();
        PriceZone z = new PriceZone(rh, rh + ENTRY_BAND_ATR * in.atr(), 0, List.of("최근고점 돌파"));
        return Optional.of(new EntryStop(z, rh - STOP_BUFFER_ATR * in.atr()));
    }

    private static Optional<EntryStop> reclaimEntry(SignalInputs in) {
        double level = (Double.isFinite(in.pivotP()) && in.pivotP() > 0) ? in.pivotP() : in.ma20();
        if (!Double.isFinite(level) || (in.price() < level && !isBoundaryChop(in, level))) return Optional.empty();
        PriceZone z = new PriceZone(level, level + ENTRY_BAND_ATR * in.atr(), 0, List.of("회복 레벨"));
        double stop = (Double.isFinite(in.recentLow()) && in.recentLow() < level)
                ? in.recentLow() - STOP_BUFFER_ATR * in.atr()
                : level - in.atr();
        return Optional.of(new EntryStop(z, stop));
    }

    /** 무릎(바닥 반전 초입): 회복 관찰 국면에서 5일선을 되찾았을 때 */
    private static Optional<EntryStop> kneeEntry(SignalInputs in) {
        double ma5 = in.ma5(), bottom = in.recentLow(), atr = in.atr();
        if (!Double.isFinite(ma5) || ma5 <= 0 || !Double.isFinite(bottom) || bottom <= 0) return Optional.empty();
        if (Double.isFinite(in.low()) && in.low() < bottom) return Optional.empty();       // 오늘 신저가 → 바닥 미확인
        if (in.price() < ma5 && !isBoundaryChop(in, ma5)) return Optional.empty();        // 5일선 회복 전
        PriceZone z = new PriceZone(ma5, ma5 + KNEE_BAND_ATR * atr, 0, List.of("5일선 회복"));
        double stop = bottom - STOP_BUFFER_ATR * atr;
        double ref = Math.max(z.low(), Math.min(in.price(), z.high()));
        if (ref - stop > KNEE_MAX_RISK_ATR * atr) return Optional.empty();
        return Optional.of(new EntryStop(z, stop));
    }

    // 판정 + 트리거 + RR(마지막 단계)
    /** 위쪽에 유의미한 저항 구간이 없을 때(신고가 돌파 등) 손익비를 가늠할 기본 목표 폭 */
    private static final double FALLBACK_TARGET_ATR = 3.0;

    public static Verdict judge(EntryType type, SignalInputs in, EntrySetup setup) {
        if (isBoundaryChop(in, setup.entry().low()) || isBoundaryChop(in, setup.entry().high())) {
            return Verdict.BOUNDARY_WATCH;
        }
        if (in.price() > setup.entry().high()) {
            return type == EntryType.PULLBACK ? Verdict.WAIT_FOR_ZONE : Verdict.CHASE_RISK;
        }
        double rr = setup.rr();
        if (!Double.isFinite(rr)) {
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

    /** 국면 때문에 정식 계획이 없을 때도, 오늘 가격이 강한 지지 구간의 경계를 오르내리는 중이면 그 구간을 보여준다 */
    public static Optional<EntrySetup> boundaryWatch(SignalInputs in, List<PriceZone> zones) {
        if (!Double.isFinite(in.open())) return Optional.empty();
        return zones.stream()
                .filter(z -> z.strength() >= MIN_SUPPORT_STRENGTH)
                .filter(z -> in.open() >= z.low())
                .filter(z -> isBoundaryChop(in, z.low()))
                .max(Comparator.comparingDouble(PriceZone::high))
                .flatMap(z -> finish(EntryType.PULLBACK, z, z.low() - STOP_BUFFER_ATR * in.atr(), in, zones));
    }

    /** 경계 공방: 오늘 가격이 이 경계를 이미 위아래로 넘나들었고 */
    static boolean isBoundaryChop(SignalInputs in, double boundary) {
        if (!in.intraday()) return false;
        if (!Double.isFinite(boundary) || !Double.isFinite(in.high()) || !Double.isFinite(in.low())
                || !Double.isFinite(in.atr()) || in.atr() <= 0) return false;
        double margin = CHOP_CROSS_ATR * in.atr();
        boolean crossedToday = in.low() < boundary - margin && in.high() > boundary + margin;
        return crossedToday && Math.abs(in.price() - boundary) <= CHOP_ATR * in.atr();
    }

    /** 시가를 아직 모르면(NaN) 트리거를 막지 않는다 */
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
    /** 익절 목표는 현재가에서 이만큼은 떨어져 있어야 의미가 있다 */
    private static final double TARGET_MIN_GAP_ATR = 0.5;
    /** 지지 구간도 최근 저점도 쓸 수 없을 때의 기본 폭 */
    private static final double HOLD_FALLBACK_ATR = 2.0;

    /** 보유 관점의 손절선(이탈 기준선). 차트 국면과 상관없이 ATR만 있으면 항상 값을 낸다 */
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

    /** 보유 관점의 익절 목표. 현재가보다 최소 0.5 ATR 위에 있는 가장 가까운 유의미한 저항 구간의 하단 */
    public static HoldingTarget holdingTarget(SignalInputs in, List<PriceZone> zones) {
        double price = in.price();
        double atr = in.atr();
        if (!(price > 0) || !(atr > 0)) return HoldingTarget.NONE;
        double floor = price + TARGET_MIN_GAP_ATR * atr;
        return zones.stream()
                .filter(z -> z.strength() >= MIN_TARGET_STRENGTH && z.low() >= floor)
                .min(Comparator.comparingDouble(PriceZone::low))
                .map(z -> new HoldingTarget(z.low(), "저항 구간 하단 (" + String.join("·", z.sources()) + ")"))
                .orElse(HoldingTarget.NONE);
    }
}
