package com.stocklog.modules.stock.analysis.diagnosis;

import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.common.finance.dto.CandleChartDto;
import com.stocklog.modules.common.finance.dto.SimpleChartDto;
import com.stocklog.modules.common.finance.indicator.phase.PhaseIndicators;
import com.stocklog.modules.common.finance.util.MathUtils;
import com.stocklog.modules.common.type.ChartPhase;
import com.stocklog.modules.stock.analysis.diagnosis.legacy.signal.SignalResultBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.stocklog.modules.common.finance.indicator.phase.PhaseConst.*;
import static com.stocklog.modules.common.finance.indicator.trend.MomentumIndicators.calcRsiWilder;
import static com.stocklog.modules.stock.analysis.diagnosis.SignalWeight.W_POC;
import static com.stocklog.modules.stock.analysis.diagnosis.SignalWeight.W_VWAP;


/**
 * 타점 엔진.
 *
 * 흐름: Phase(PhaseIndicators) -> 진입 유형 -> 가격 구간(Zone) -> 진입 구간 -> 구조적 손절
 *       -> 구조적 목표 -> RR(마지막 feasibility 체크) -> 가중치
 *
 * ATR은 "진입가를 정하는 근거"가 아니라 구간 클러스터링 폭, 손절 버퍼, 추격 여부 판단에만 쓴다.
 */
@Component
public class TradingSignalAnalyzer {

    private static final int LEVEL_HIGH_LOOKBACK = 60; // 돌파 기준 고점 구간 (오늘 제외)
    private static final int LEVEL_LOW_LOOKBACK  = 20; // 스윙 저점 구간 (오늘 제외)


    /** 신규 진입 계획을 세울 수 있는 Phase. STRONG_HOLD는 보유 관점이라 제외한다. */
    private static final Set<ChartPhase> PLANNABLE = EnumSet.of(
            ChartPhase.ENTRY_READY, ChartPhase.RE_ENTRY,
            ChartPhase.COOLDOWN, ChartPhase.RECOVERY_WATCH);

    /** 정식 타점이 없어도 참고 가격대를 보여줄 Phase. 하락·손절 계열은 일부러 제외한다. */
    private static final Set<ChartPhase> REFERENCE_PHASES = EnumSet.of(
            ChartPhase.WATCH, ChartPhase.SETUP, ChartPhase.ENTRY_READY, ChartPhase.RE_ENTRY,
            ChartPhase.COOLDOWN, ChartPhase.STRONG_HOLD, ChartPhase.RECOVERY_WATCH,
            ChartPhase.WARNING_ZONE, ChartPhase.SCALE_OUT, ChartPhase.CLIMAX);

    // ------------------------------------------------------------------
    // 결과 모델
    // ------------------------------------------------------------------
    public enum Verdict {
        ACTIONABLE,     // 진입 구간 안 + 트리거 확인 + RR 통과
        WAIT_FOR_ZONE,  // 눌림 구간까지 내려오길 대기
        WAIT_TRIGGER,   // 구간 안이지만 반전/확인 캔들 미발생
        CHASE_RISK,     // 돌파/탈환 구간을 이미 벗어남
        RR_TOO_LOW,     // 구조상 손익비 부족
        NO_SETUP        // 계획 자체가 성립하지 않음
    }

    public record Zone(double low, double high, double strength, List<String> sources) {
        public double mid() { return (low + high) / 2; }
    }

    /** target1/target2/rr은 구조적 저항 구간이 없으면 NaN (임의 투영값으로 RR을 만들지 않는다). */
    public record TradePlan(EntryType type, Zone entry, double refPrice,
                            double stop, double target1, double target2, double rr) {
        public boolean hasStructuralTarget() { return Double.isFinite(target1); }
    }

    // ------------------------------------------------------------------
    // 진입점
    // ------------------------------------------------------------------
    public TradingSignalResult analyze(String symbol, MarketContext ctx) {
        ChartPhase phase = PhaseIndicators.determinePhase(ctx);
        if (phase == ChartPhase.INSUFFICIENT_DATA) {
            return TradingSignalResult.createFallback(symbol, "INSUFFICIENT_DATA");
        }

        SignalResultBuilder builder = new SignalResultBuilder();
        indicatorSignals(ctx, builder);
        SpecificSignalsBuilder.addPhaseSpecificSignals(phase, ctx, builder);

        Inputs in = Inputs.from(ctx);
        boolean atrOk = Double.isFinite(in.atr()) && in.atr() > 0; // ATR 없이는 구간/손절 계산 불가
        List<Zone> zones = atrOk ? cluster(levels(in), in.atr()) : List.of();

        TradePlan plan = null;
        Verdict verdict = Verdict.NO_SETUP;
        if (atrOk && PLANNABLE.contains(phase)) {
            EntryType type = PhaseIndicators.classifyEntryType(ctx, phase);
            if (isPlannable(type)) {
                plan = buildPlan(type, in, zones).orElse(null);
                if (plan != null) {
                    verdict = judge(type, in, plan);
                    report(verdict, plan, builder);
                }
            }
        }

        if (plan == null && atrOk && REFERENCE_PHASES.contains(phase)) {
            plan = referencePlan(in, zones).orElse(null);
        }

        if (atrOk && verdict != Verdict.ACTIONABLE) {
            explain(in, zones, ctx, builder); // 진입 신호가 없어도 "어디를 보고 무엇을 기다릴지"는 알려준다
        }

        double trailing = atrOk ? trailingStop(phase, in, zones) : 0.0;
        TradingSignalResult.EntryPlan entryPlan = entryPlan(plan, in, zones);
        TradingSignalResult.Narrative narrative = PhaseNarrative.build(phase, ctx, verdict, plan);
        return toDto(symbol, phase, ctx, verdict, plan, entryPlan, narrative, trailing, builder.build());
    }

    // ------------------------------------------------------------------
    // DTO 매핑 (DTO는 "없음"을 0.0으로 표현하므로 NaN은 0.0으로 변환)
    // ------------------------------------------------------------------
    private static TradingSignalResult toDto(String symbol, ChartPhase phase, MarketContext ctx,
                                             Verdict verdict, TradePlan plan,
                                             TradingSignalResult.EntryPlan entryPlan, TradingSignalResult.Narrative narrative, double trailing,
                                             SignalResultBuilder.SignalResult r) {
        return new TradingSignalResult(
                symbol != null ? symbol : "UNKNOWN",
                new TradingSignalResult.SignalStrength(
                        r.buySignals(), r.sellSignals(), r.watchSignals(),
                        r.buyStrength(), r.sellStrength()),
                new TradingSignalResult.ChartAnalysis(phase, ctx),
                entryPlan,
                narrative,
                priceTarget(plan, trailing),
                riskOf(verdict, plan),
                verdict.name(),
                LocalDateTime.now());
    }

    /**
     * 진입 계획 요약. 계획이 없으면 EntryPlan.none().
     * - zoneStrength: 진입 구간과 겹치는 레벨 클러스터의 최대 강도 (돌파/탈환은 해당 레벨의 합산 가중치)
     * - riskReward: 구조적 목표가 없으면 0.0 (산출 불가)
     * - triggerConfirmed: 현재가가 구간 안이고 당일 양봉이 확인된 경우만 true.
     *   시가를 모르면(NaN) false다. judge()는 이 경우 트리거를 막지 않으므로 ACTIONABLE이어도 false일 수 있다.
     */
    private static TradingSignalResult.EntryPlan entryPlan(TradePlan plan, Inputs in, List<Zone> zones) {
        if (plan == null) return TradingSignalResult.EntryPlan.none();

        Zone entry = plan.entry();
        double strength = zones.stream()
                .filter(z -> z.low() <= entry.high() && z.high() >= entry.low())
                .mapToDouble(Zone::strength)
                .max().orElse(0.0);
        boolean inZone = in.price() <= entry.high();
        boolean trigger = inZone && Double.isFinite(in.open()) && in.price() > in.open();

        return new TradingSignalResult.EntryPlan(
                plan.type(), entry.low(), entry.high(), strength, nz(plan.rr()), trigger);
    }

    private static TradingSignalResult.PriceTarget priceTarget(TradePlan plan, double trailing) {
        if (plan == null) {
            return new TradingSignalResult.PriceTarget(0, 0, 0, 0, 0, trailing);
        }
        // primary: 지금 체결을 가정한 기준가(구간 안이면 현재가, 위면 구간 상단), secondary: 구간 하단
        return new TradingSignalResult.PriceTarget(
                plan.refPrice(), plan.entry().low(), plan.stop(),
                nz(plan.target1()), nz(plan.target2()), trailing);
    }

    /**
     * 고정 위험 비율 사이징: 비중(%) = 허용 손실(%) / 손절까지 거리(%) x 100, 상한 적용.
     * 비중은 ACTIONABLE일 때만 산출하며, 구조적 목표가 없으면 절반만 반영한다.
     */
    private static TradingSignalResult.RiskManagement riskOf(Verdict verdict, TradePlan plan) {
        if (plan == null) return TradingSignalResult.RiskManagement.zero();

        double riskPerShare = plan.refPrice() - plan.stop();
        double riskPct = riskPerShare / plan.refPrice() * 100;
        double weight = 0;
        if (verdict == Verdict.ACTIONABLE) {
            weight = Math.min(MAX_POSITION_PCT, RISK_BUDGET_PCT / riskPct * 100);
            if (!plan.hasStructuralTarget()) weight *= 0.5;
            weight = Math.round(weight * 10) / 10.0;
        }
        return new TradingSignalResult.RiskManagement(riskPerShare, riskPct, weight);
    }

    /**
     * 보유 관점의 추적 손절: 현재가 아래 가장 가까운 유의미한 지지 구간 하단 - 버퍼.
     * 이 함수는 무상태라서, 호출하는 쪽이 이전 값과 max()를 취해 아래로 내려가지 않게 해야 한다.
     */
    private static double trailingStop(ChartPhase phase, Inputs in, List<Zone> zones) {
        if (phase != ChartPhase.STRONG_HOLD && !PLANNABLE.contains(phase)) return 0.0;
        return zones.stream()
                .filter(z -> z.strength() >= MIN_SUPPORT_STRENGTH && z.high() < in.price())
                .max(Comparator.comparingDouble(Zone::high))
                .map(z -> z.low() - STOP_BUFFER_ATR * in.atr())
                .orElse(0.0);
    }

    /** 진입 계획이 없거나 대기 중일 때: 가까운 지지/저항 구간과 과열 여부를 관찰 신호로 남긴다. */
    private static void explain(Inputs in, List<Zone> zones, MarketContext ctx, SignalResultBuilder b) {
        double price = in.price();

        zones.stream()
                .filter(z -> z.high() < price && z.strength() >= MIN_SUPPORT_STRENGTH)
                .max(Comparator.comparingDouble(Zone::high))
                .ifPresent(z -> b.addWatchSignal(String.format(
                        "🧭 [가까운 지지] %s~%s (%s), 현재가 대비 %+.1f%%",
                        fmt(z.low()), fmt(z.high()), String.join("·", z.sources()),
                        (z.high() - price) / price * 100)));

        zones.stream()
                .filter(z -> z.low() > price && z.strength() >= MIN_TARGET_STRENGTH)
                .min(Comparator.comparingDouble(Zone::low))
                .ifPresent(z -> b.addWatchSignal(String.format(
                        "🧭 [위쪽 저항] %s~%s (%s), 현재가 대비 %+.1f%%",
                        fmt(z.low()), fmt(z.high()), String.join("·", z.sources()),
                        (z.low() - price) / price * 100)));

        double rsi = ctx.momentum().rsi();
        if (Double.isFinite(rsi) && rsi >= WARNING_RSI) {
            b.addWatchSignal(String.format(
                    "🌡️ [과열권] RSI %.0f. 신규 진입은 지지 구간까지 눌린 뒤 거래량 감소와 반등이 확인될 때 판단", rsi));
        }
    }

    private static double nz(double v) {
        return Double.isFinite(v) ? v : 0.0;
    }

    /** NONE, INSUFFICIENT_DATA는 계획 대상이 아니다. */
    private static boolean isPlannable(EntryType type) {
        return type == EntryType.PULLBACK || type == EntryType.BREAKOUT || type == EntryType.RECLAIM;
    }

    // ------------------------------------------------------------------
    // 지표 이벤트 신호 (구 SignalRule 3종을 이관: Momentum / RSI / TrendAndMa)
    // 판정(Verdict)에는 관여하지 않고, 근거 문구와 buy/sell 강도만 builder에 쌓는다.
    // 이관 시 신호 조건과 가중치는 원본 그대로이며, 방어 코드만 추가했다:
    //  - volume null일 때 NPE 나던 VWAP 구간
    //  - POC 값이 비정상(NaN/0)일 때 0 나눗셈
    //  - 다이버전스: 봉이 15개 미만이라 RSI(14)를 계산할 수 없을 때
    // ------------------------------------------------------------------
    private static final int W_RSI_EXTREME = SignalWeight.RSI_EXTREME.getWeight();

    private static void indicatorSignals(MarketContext ctx, SignalResultBuilder b) {
        momentumSignals(ctx, b);
        rsiSignals(ctx, b);
        trendAndMaSignals(ctx, b);
    }

    /** MACD 크로스 + 볼린저 밴드 이탈/돌파 */
    private static void momentumSignals(MarketContext ctx, SignalResultBuilder b) {
        int macdW = SignalWeight.MACD_CROSS.getWeight()
                + ((ctx.momentum() != null && ctx.momentum().isStrongTrend()) ? 10 : 0);
        int bbW = SignalWeight.BOLLINGER_BREAK.getWeight();
        int volW = SignalWeight.VOL_CONFIRM.getWeight();

        if (ctx.macd().isGoldenCross()) {
            b.addBuySignal(String.format("MACD 골든크로스 (MACD: %.2f / Signal: %.2f)", ctx.macd().macd(), ctx.macd().signal()));
            b.addBuyWeight(macdW);
        } else if (ctx.macd().isDeadCross()) {
            b.addSellSignal(String.format("MACD 데드크로스 (MACD: %.2f / Signal: %.2f)", ctx.macd().macd(), ctx.macd().signal()));
            b.addSellWeight(SignalWeight.MACD_CROSS.getWeight());
        }

        boolean volBearish = ctx.volume() != null && ctx.volume().isVolBearish();
        if (ctx.currentPrice() < ctx.bollingerBand().lower()) {
            if (volBearish) {
                b.addSellSignal(String.format("🚨 볼린저 하단 강한 이탈 (투매 주의) %%B: %.2f", ctx.bollingerBand().percentB()));
                b.addSellWeight(bbW * 2);
            } else {
                b.addBuySignal(String.format("볼린저 하단 이탈 (단기 낙폭과대) %%B: %.2f", ctx.bollingerBand().percentB()));
                b.addBuyWeight(bbW);
            }
        } else if (ctx.currentPrice() > ctx.bollingerBand().upper()) {
            // 상단 돌파의 "거래량 동반"은 상승 거래량(isVolBullish)이어야 한다.
            // (원본은 isVolBearish로 되어 있어 돌파가 매도 쪽으로 해석되던 것을 수정)
            boolean volBullish = ctx.volume() != null && ctx.volume().isVolBullish();
            if (volBullish) {
                if (ctx.bollingerBand().isSqueeze()) {
                    b.addBuySignal(String.format("🚀 [스퀴즈 폭발] 볼린저 밴드 응축 후 상단 돌파 (상단 %.0f)", ctx.bollingerBand().upper()));
                    b.addBuyWeight(bbW * 2 + volW);
                } else {
                    b.addBuySignal(String.format("볼린저 상단 돌파 (거래량 동반) — 강한 추세 (상단 %.0f)", ctx.bollingerBand().upper()));
                    b.addBuyWeight(bbW + volW);
                }
            } else {
                b.addSellSignal(String.format("볼린저 상단 이탈 (단기 과열) (현재 %.0f / 상단 %.0f)", ctx.currentPrice(), ctx.bollingerBand().upper()));
                b.addSellWeight(bbW);
            }
        }
    }

    /** RSI 과매수/과매도 + 다이버전스 */
    private static void rsiSignals(MarketContext ctx, SignalResultBuilder b) {
        double rsi = ctx.momentum() != null ? ctx.momentum().rsi() : 0;
        int rangeModifier = ctx.momentum() != null && ctx.momentum().isWeakTrend() ? 10 : 0; // 횡보장은 RSI 비중↑

        if (rsi < 25) {
            b.addBuySignal(String.format("RSI %.1f — 극단적 과매도 (강한 반등 신호)", rsi));
            b.addBuyWeight(W_RSI_EXTREME + rangeModifier);
        } else if (rsi < 35) {
            b.addBuySignal(String.format("RSI %.1f — 과매도 구간", rsi));
            b.addBuyWeight(W_RSI_EXTREME / 2);
        } else if (rsi > 75) {
            if (ctx.movingAverages().isMa5Rising()
                    && ctx.movingAverages().distFromMa5() < ctx.bollingerBand().dynamicThreshold()) {
                b.addSellSignal(String.format("RSI %.1f (과매수) — 추세 이탈 없음 (익절 보류)", rsi));
                b.addSellWeight(W_RSI_EXTREME / 2);
            } else {
                b.addSellSignal(String.format("RSI %.1f — 극단적 과매수 및 이격 과열", rsi));
                b.addSellWeight(W_RSI_EXTREME);
            }
        } else if (rsi > 65) {
            b.addSellSignal(String.format("RSI %.1f — 과매수 구간", rsi));
            b.addSellWeight(W_RSI_EXTREME / 2);
        }

        rsiDivergence(ctx, b, rsi);
    }

    private static void rsiDivergence(MarketContext ctx, SignalResultBuilder b, double currentRsi) {
        final int lookback = 20;
        List<Double> closes = ctx.priceSeries().closes();
        int lastIdx = ctx.priceSeries().lastIdx();
        double price = ctx.currentPrice();
        if (closes == null || lastIdx < 15 || lastIdx >= closes.size()) return; // RSI(14) 계산 불가 구간 방어

        // 하락 다이버전스: 주가는 고점 경신, RSI는 직전 고점 대비 낮고 직전 RSI가 과매수권
        int peakIdx = lastIdx - 1;
        double peakClose = closes.get(lastIdx - 1);
        for (int i = lastIdx - 1; i >= Math.max(14, lastIdx - lookback); i--) {
            if (closes.get(i) > peakClose) { peakClose = closes.get(i); peakIdx = i; }
        }
        double peakRsi = calcRsiWilder(closes, 14, peakIdx);
        if (price >= peakClose && currentRsi < peakRsi && peakRsi > 65) {
            b.addSellSignal(String.format("🚨 [하락 다이버전스] 주가는 고점 경신(%.0f), RSI는 하락(%.1f → %.1f) — 강력한 매도 경고",
                    price, peakRsi, currentRsi));
            b.addSellWeight((int) (W_RSI_EXTREME * 1.5));
        }

        // 상승 다이버전스: 주가는 저점 경신, RSI는 직전 저점 대비 높고 직전 RSI가 과매도권
        int troughIdx = lastIdx - 1;
        double troughClose = closes.get(lastIdx - 1);
        for (int i = lastIdx - 1; i >= Math.max(14, lastIdx - lookback); i--) {
            if (closes.get(i) < troughClose) { troughClose = closes.get(i); troughIdx = i; }
        }
        double troughRsi = calcRsiWilder(closes, 14, troughIdx);
        if (price <= troughClose && currentRsi > troughRsi && troughRsi < 35) {
            b.addBuySignal(String.format("💎 [상승 다이버전스] 주가는 저점 경신(%.0f), RSI는 상승(%.1f → %.1f) — 바닥 탈출 징후",
                    price, troughRsi, currentRsi));
            b.addBuyWeight((int) (W_RSI_EXTREME * 1.5));
        }
    }

    /** 5일선 추세/이격 + 피봇 + 매물대(POC) + VWAP */
    private static void trendAndMaSignals(MarketContext ctx, SignalResultBuilder b) {
        int weight = SignalWeight.MA_CROSS.getWeight();
        var ma = ctx.movingAverages();
        double price = ctx.currentPrice();

        if (price > ma.ma5()) {
            if (ma.isMa5Rising()) {
                if (ma.distFromMa5() > ctx.bollingerBand().dynamicThreshold()) {
                    b.addSellSignal(String.format("[이격과열] 5일선 대비 +%.1f%% — 조정 주의", ma.distFromMa5()));
                    b.addSellWeight(weight);
                } else {
                    b.addBuySignal(String.format("[5일선 상방] 안착 및 탄력 유지 (기준: %.0f)", ma.ma5()));
                    b.addBuyWeight(weight);
                }
            } else {
                b.addBuySignal(String.format("5일선 돌파(기울기 하락) — 안착 확인 필요 (MA5: %.0f)", ma.ma5()));
                b.addBuyWeight(weight / 2);
            }
        } else if (!ma.isMa5Rising()) {
            if (Math.abs(ma.distFromMa5()) > ctx.bollingerBand().dynamicThreshold()) {
                b.addBuySignal(String.format("[과대낙폭] 5일선 이격 %.1f%% — 반등 기대", ma.distFromMa5()));
                b.addBuyWeight(weight);
            } else {
                b.addSellSignal(String.format("[5일선 하방] 단기 추세 꺾임 (기준: %.0f)", ma.ma5()));
                b.addSellWeight(weight);
            }
        }

        // 피봇 지지/저항
        int pivotW = SignalWeight.PIVOT_LEVEL.getWeight();
        double yesterday = ctx.priceSeries().yesterdayPrice();
        var pv = ctx.pivots().dailyPivot();
        double pp = pv.p();
        if (price > pp && yesterday <= pp) {
            b.addBuySignal(String.format("피벗 기준선 상향 돌파 — 단기 추세 전환 (PP: %.0f)", pp));
            b.addBuyWeight(pivotW);
        } else if (price < pp && yesterday >= pp) {
            b.addSellSignal(String.format("피벗 기준선 하향 이탈 — 단기 하방 압력 (PP: %.0f)", pp));
            b.addSellWeight(pivotW);
        }
        if (price <= pv.s2() * 1.01) {
            b.addBuySignal(String.format("피벗 2차 지지선 도달 — 강한 반등 기대 (S2: %.0f)", pv.s2()));
            b.addBuyWeight(pivotW);
        }
        if (price >= pv.r2() * 0.99) {
            b.addSellSignal(String.format("피벗 2차 저항선 도달 — 과열 주의 (R2: %.0f)", pv.r2()));
            b.addSellWeight(pivotW);
        }

        if (ctx.volume() == null) return; // 이하 거래량 기반 신호

        // 매물대(POC): 2% 이내 근접 시 지지/저항 테스트
        double poc = ctx.volume().pocPrice();
        if (Double.isFinite(poc) && poc > 0 && Math.abs((price - poc) / poc * 100) <= 2.0) {
            if (price >= poc) {
                b.addBuySignal(String.format("🎯 [매물대 지지] 최대 매물대(POC: %.0f) 안착 테스트 중", poc));
                b.addBuyWeight(W_POC.getWeight());
            } else {
                b.addSellSignal(String.format("🧱 [매물대 저항] 최대 매물대(POC: %.0f) 하단 진입 — 돌파 확인 필요", poc));
                b.addSellWeight(W_POC.getWeight());
            }
        }

        // VWAP 돌파/이탈
        double vwap = ctx.volume().vwap20();
        if (price > vwap && yesterday <= vwap) {
            b.addBuySignal(String.format("🚀 [세력선 돌파] 기관 평균단가(VWAP: %.0f) 상향 돌파", vwap));
            b.addBuyWeight(W_VWAP.getWeight());
        } else if (price < vwap && yesterday >= vwap) {
            b.addSellSignal(String.format("📉 [세력선 이탈] 기관 평균단가(VWAP: %.0f) 하향 이탈", vwap));
            b.addSellWeight(W_VWAP.getWeight());
        }
    }

    // ------------------------------------------------------------------
    // 입력 어댑터: MarketContext에서 가격 레벨과 ATR을 뽑는 유일한 지점
    // 아직 접근자를 모르는 값은 NaN으로 두며, 그 레벨은 자동으로 무시된다.
    // ------------------------------------------------------------------
    record Inputs(double price, double open, double high, double low, double atr,
                  double recentHigh, double recentLow,
                  double ma20, double ma50, double vwap20, double poc,
                  double bbLower, double bbUpper,
                  double pivotP, double s1, double s2, double s3, double r1) {

        static Inputs from(MarketContext ctx) {
            var pv = ctx.pivots().dailyPivot();
            var ma = ctx.movingAverages();
            var series = ctx.priceSeries();
            int lastIdx = series.lastIdx();

            // 당일 시가/고가/저가: 캔들 DTO일 때만 채운다. 아니면 NaN (트리거/꼬리 판정만 비활성)
            double open = Double.NaN, high = Double.NaN, low = Double.NaN;
            List<? extends SimpleChartDto> charts = series.dailyCharts();
            if (charts != null && lastIdx >= 0 && lastIdx < charts.size()
                    && charts.get(lastIdx) instanceof CandleChartDto candle) {
                open = candle.open().doubleValue();
                high = candle.high().doubleValue();
                low = candle.low().doubleValue();
            }

            // 오늘 봉은 제외한다. 오늘 고가가 포함되면 price >= recentHigh가 사실상 종가=고가일 때만 성립해 돌파가 안 잡힌다.
            // 저점은 손절/지지 기준이므로 60봉 극값이 아니라 가까운 스윙 저점 구간을 쓴다.
            double recentHigh = MathUtils.highest(series.highs(), lastIdx - 1, LEVEL_HIGH_LOOKBACK);
            double recentLow  = MathUtils.lowest(series.lows(), lastIdx - 1, LEVEL_LOW_LOOKBACK);

            return new Inputs(
                    ctx.currentPrice(),
                    open, high, low,
                    ctx.momentum().atr(),
                    recentHigh, recentLow,
                    ma.ma20(), ma.ma50(),
                    ctx.volume().vwap20(), ctx.volume().pocPrice(),
                    ctx.bollingerBand().lower(), ctx.bollingerBand().upper(),
                    pv.p(), pv.s1(), pv.s2(), pv.s3(), pv.r1());
        }
    }

    // ------------------------------------------------------------------
    // 가격 레벨 -> 구간(Zone)
    // ------------------------------------------------------------------
    private record Level(String source, double price, double weight) {}

    private static List<Level> levels(Inputs in) {
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

    /** ATR 이내로 붙어 있는 레벨을 하나의 구간으로 묶는다. (연쇄 클러스터링: 인접 레벨 기준) */
    private static List<Zone> cluster(List<Level> levels, double atr) {
        List<Level> sorted = levels.stream()
                .sorted(Comparator.comparingDouble(Level::price)).toList();
        List<Zone> zones = new ArrayList<>();
        List<Level> group = new ArrayList<>();
        for (Level lv : sorted) {
            if (!group.isEmpty()
                    && lv.price() - group.get(group.size() - 1).price() > atr * CLUSTER_ATR) {
                zones.add(toZone(group, atr));
                group = new ArrayList<>();
            }
            group.add(lv);
        }
        if (!group.isEmpty()) zones.add(toZone(group, atr));
        return zones;
    }

    private static Zone toZone(List<Level> group, double atr) {
        double lo = group.get(0).price();
        double hi = group.get(group.size() - 1).price();
        double minWidth = atr * MIN_ZONE_WIDTH_ATR;
        if (hi - lo < minWidth) {
            double mid = (hi + lo) / 2;
            lo = mid - minWidth / 2;
            hi = mid + minWidth / 2;
        }
        double strength = group.stream().mapToDouble(Level::weight).sum();
        return new Zone(lo, hi, strength, group.stream().map(Level::source).toList());
    }

    // ------------------------------------------------------------------
    // 진입 유형별 진입 구간 + 구조적 손절
    // ------------------------------------------------------------------
    private record EntryStop(Zone zone, double stop) {}

    private static Optional<TradePlan> buildPlan(EntryType type, Inputs in, List<Zone> zones) {
        Optional<EntryStop> es = switch (type) {
            case PULLBACK -> pullbackEntry(in, zones);
            case BREAKOUT -> breakoutEntry(in);
            case RECLAIM  -> reclaimEntry(in);
            case NONE, INSUFFICIENT_DATA -> Optional.empty();
        };
        return es.flatMap(x -> finishPlan(type, x.zone(), x.stop(), in, zones));
    }

    /** 진입 구간과 손절이 정해진 뒤 기준가, 목표, RR을 채워 계획을 완성한다. */
    private static Optional<TradePlan> finishPlan(EntryType type, Zone entry, double stop,
                                                  Inputs in, List<Zone> zones) {
        // 구간 안이면 현재가, 구간 위면 구간 상단(보수적 체결 가정)을 기준가로 쓴다
        double ref = Math.min(in.price(), entry.high());
        double risk = ref - stop;
        if (risk <= 0) return Optional.empty();

        // 목표: 진입 구간과 "현재가" 둘 다보다 위에 있는 가장 가까운 유의미한 저항 구간의 하단. 없으면 NaN.
        // (현재가가 이미 구간 위로 올라와 있으면, 지나온 가격대는 목표가 될 수 없다)
        double floor = Math.max(entry.high(), in.price());
        double[] targets = zones.stream()
                .filter(z -> z.low() > floor && z.strength() >= MIN_TARGET_STRENGTH)
                .mapToDouble(Zone::low)
                .sorted().limit(2).toArray();
        double target1 = targets.length > 0 ? targets[0] : Double.NaN;
        double target2 = targets.length > 1 ? targets[1] : Double.NaN;
        double rr = Double.isFinite(target1) ? (target1 - ref) / risk : Double.NaN;

        return Optional.of(new TradePlan(type, entry, ref, stop, target1, target2, rr));
    }

    /**
     * 참고 가격대: 정식 진입 계획(PULLBACK/BREAKOUT/RECLAIM)이 없어도,
     * 현재가 아래 가장 가까운 유의미한 지지 구간을 "관심 가격대"로 보여준다.
     * type은 NONE이라 화면에서 정식 타점과 구분되고, 비중은 0이다.
     */
    private static Optional<TradePlan> referencePlan(Inputs in, List<Zone> zones) {
        return zones.stream()
                .filter(z -> z.strength() >= MIN_SUPPORT_STRENGTH && z.high() < in.price())
                .max(Comparator.comparingDouble(Zone::high))
                .flatMap(z -> finishPlan(EntryType.NONE, z, z.low() - STOP_BUFFER_ATR * in.atr(), in, zones));
    }

    private static Optional<EntryStop> pullbackEntry(Inputs in, List<Zone> zones) {
        return zones.stream()
                .filter(z -> z.strength() >= MIN_SUPPORT_STRENGTH)
                .filter(z -> z.low() <= in.price())
                .filter(z -> (in.price() - z.high()) / in.atr() <= ENTRY_MAX_DIST_ATR)
                .max(Comparator.comparingDouble(Zone::high))
                .map(z -> new EntryStop(z, z.low() - STOP_BUFFER_ATR * in.atr()));
    }

    private static Optional<EntryStop> breakoutEntry(Inputs in) {
        double rh = in.recentHigh();
        if (!Double.isFinite(rh) || in.price() < rh) return Optional.empty();
        Zone z = new Zone(rh, rh + ENTRY_BAND_ATR * in.atr(), 0, List.of("최근고점 돌파"));
        return Optional.of(new EntryStop(z, rh - STOP_BUFFER_ATR * in.atr()));
    }

    private static Optional<EntryStop> reclaimEntry(Inputs in) {
        double level = (Double.isFinite(in.pivotP()) && in.pivotP() > 0) ? in.pivotP() : in.ma20();
        if (!Double.isFinite(level) || in.price() < level) return Optional.empty();
        Zone z = new Zone(level, level + ENTRY_BAND_ATR * in.atr(), 0, List.of("회복 레벨"));
        double stop = (Double.isFinite(in.recentLow()) && in.recentLow() < level)
                ? in.recentLow() - STOP_BUFFER_ATR * in.atr()
                : level - in.atr();
        return Optional.of(new EntryStop(z, stop));
    }

    // ------------------------------------------------------------------
    // 판정 + 트리거 + RR(마지막 단계)
    // ------------------------------------------------------------------
    private static Verdict judge(EntryType type, Inputs in, TradePlan plan) {
        if (in.price() > plan.entry().high()) {
            return type == EntryType.PULLBACK ? Verdict.WAIT_FOR_ZONE : Verdict.CHASE_RISK;
        }
        if (Double.isFinite(plan.rr()) && plan.rr() < MIN_RR) {
            return Verdict.RR_TOO_LOW;
        }
        if (!bullishTrigger(in)) {
            return Verdict.WAIT_TRIGGER;
        }
        return Verdict.ACTIONABLE;
    }

    /** 시가를 아직 모르면(NaN) 트리거를 막지 않는다. 시가가 들어오면 양봉을 요구한다. */
    private static boolean bullishTrigger(Inputs in) {
        if (!Double.isFinite(in.open())) return true;
        return in.price() > in.open();
    }

    /** 몸통/꼬리는 전일 종가가 아니라 당일 시가-종가로 계산해야 한다. 반전 캔들 판정에 사용. */
    static double lowerWickRatio(Inputs in) {
        double range = in.high() - in.low();
        if (!Double.isFinite(range) || range <= 0) return 0;
        return (Math.min(in.open(), in.price()) - in.low()) / range;
    }

    static double upperWickRatio(Inputs in) {
        double range = in.high() - in.low();
        if (!Double.isFinite(range) || range <= 0) return 0;
        return (in.high() - Math.max(in.open(), in.price())) / range;
    }

    // ------------------------------------------------------------------
    // 문구 + 가중치. 매수 가중치는 ACTIONABLE일 때만 부여한다.
    // ------------------------------------------------------------------
    private static void report(Verdict verdict, TradePlan p, SignalResultBuilder b) {
        String zone = String.format("진입 구간 %s~%s (%s)",
                fmt(p.entry().low()), fmt(p.entry().high()), String.join("·", p.entry().sources()));
        String risk = String.format("구조적 손절 %s", fmt(p.stop()));
        String tgt = p.hasStructuralTarget()
                ? String.format("1차 목표 %s, RR %.1f", fmt(p.target1()), p.rr())
                : "위쪽에 뚜렷한 저항 구간 없음(RR 산출 불가)";
        String label = p.type().getTitle();

        switch (verdict) {
            case ACTIONABLE -> b.addBuySignal(
                    String.format("🎯 [%s 진입 구간] %s / %s / %s", label, zone, risk, tgt),
                    weight(p));
            case WAIT_FOR_ZONE -> b.addWatchSignal(
                    String.format("⏳ [%s 대기] 현재가가 구간 위. %s / %s", label, zone, risk));
            case WAIT_TRIGGER -> b.addWatchSignal(
                    String.format("⏳ [%s 확인 대기] 구간 안이나 반등 확인(양봉) 전. %s", label, zone));
            case CHASE_RISK -> b.addWatchSignal(
                    String.format("⚠️ [추격 주의] %s 구간을 이미 벗어남. %s", label, zone));
            case RR_TOO_LOW -> b.addWatchSignal(
                    String.format("⚖️ [손익비 부족] %s. %s / %s", label, risk, tgt));
            case NO_SETUP -> { }
        }
    }

    private static int weight(TradePlan p) {
        double base = switch (p.type()) {
            case PULLBACK -> BASE_PULLBACK;
            case BREAKOUT -> BASE_BREAKOUT;
            case RECLAIM  -> BASE_RECLAIM;
            case NONE, INSUFFICIENT_DATA -> 0.0;
        };
        // 구조적 목표가 없으면 RR을 검증할 수 없으므로 절반만 반영
        double factor = p.hasStructuralTarget() ? Math.min(1.3, Math.max(0.6, p.rr() / 2.0)) : 0.5;
        return (int) Math.round(base * factor);
    }

    private static String fmt(double v) {
        return v >= 1000 ? String.format("%,.0f", v) : String.format("%,.2f", v);
    }
}