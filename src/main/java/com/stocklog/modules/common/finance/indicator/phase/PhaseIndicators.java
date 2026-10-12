package com.stocklog.modules.common.finance.indicator.phase;


import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.common.type.ChartPhase;
import com.stocklog.modules.stock.analysis.signal.trading.EntryType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.*;

import static com.stocklog.modules.common.finance.indicator.phase.PhaseConst.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PhaseIndicators {

    private static final Set<ChartPhase> ENTRY_BLOCKED = EnumSet.of(
            ChartPhase.CLIMAX, ChartPhase.WARNING_ZONE, ChartPhase.SCALE_OUT,
            ChartPhase.STOP_LOSS, ChartPhase.CAPITULATION,
            ChartPhase.DOWNTREND_CONFIRMED, ChartPhase.FAKE_OUT);
    public static ChartPhase determinePhase(MarketContext context) {
        if (!isValid(context)) {
            return ChartPhase.INSUFFICIENT_DATA;
        }
        return checkExtremes(context)
                .or(() -> checkTopSignals(context))
                .or(() -> checkUptrend(context))
                .or(() -> checkPullback(context))
                .or(() -> checkSetup(context))
                .or(() -> checkBearish(context))
                .orElse(ChartPhase.WATCH);
    }

    // ------------------------------------------------------------------
    // EntryType 분류 (타점 판별)
    // ------------------------------------------------------------------
    public static EntryType classifyEntryType(MarketContext context) {
        return classifyEntryType(context, determinePhase(context));
    }

    /** 이미 계산한 Phase를 재사용하는 오버로드 (중복 계산 방지). */
    public static EntryType classifyEntryType(MarketContext ctx, ChartPhase phase) {
        if (phase == ChartPhase.INSUFFICIENT_DATA) {
            return EntryType.INSUFFICIENT_DATA;
        }
        if (ENTRY_BLOCKED.contains(phase)) {
            return EntryType.NONE;
        }
        boolean macroUp = ctx.priceAction().isMacroUptrend();

        // 돌파/눌림은 상승 구간 전용, 탈환은 회복 구간에서도 허용
        if (macroUp && isBreakOut(ctx)) return EntryType.BREAKOUT;
        if (isReclaim(ctx))             return EntryType.RECLAIM;
        if (macroUp && isPullback(ctx)) return EntryType.PULLBACK;

        return EntryType.NONE;
    }

    // ------------------------------------------------------------------
    // 0. 입력 검증: 두 경로(determinePhase / classifyEntryType)에서 쓰는 모든 수치
    // ------------------------------------------------------------------
    private static boolean isValid(MarketContext ctx) {
        if (ctx == null
                || ctx.priceAction() == null
                || ctx.movingAverages() == null
                || ctx.volume() == null
                || ctx.momentum() == null
                || ctx.bollingerBand() == null
                || ctx.macd() == null
                || ctx.pivots() == null
                || ctx.pivots().dailyPivot() == null) {
            return false;
        }

        double price = ctx.currentPrice();
        if (!Double.isFinite(price) || price <= 0) {
            return false;
        }

        var pivot = ctx.pivots().dailyPivot();
        var ma = ctx.movingAverages();
        var vol = ctx.volume();
        var bb = ctx.bollingerBand();

        return allFinite(
                ctx.momentum().rsi(),
                ma.ma5(), ma.ma20(), ma.distFromMa20(),
                vol.vwap20(), vol.volumeMa20(), vol.currentVolume(),
                bb.percentB(), bb.upper(),
                pivot.p(), pivot.r1(), pivot.r3(), pivot.s2(), pivot.s3()
        );
    }

    private static boolean allFinite(double... values) {
        for (double v : values) {
            if (!Double.isFinite(v)) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 1. 극단적 상황 및 리스크 관리 (최우선)
    // ------------------------------------------------------------------
    private static Optional<ChartPhase> checkExtremes(MarketContext ctx) {
        var pa = ctx.priceAction();
        var vol = ctx.volume();
        var mom = ctx.momentum();
        var bb = ctx.bollingerBand();
        var pivot = ctx.pivots().dailyPivot();
        double price = ctx.currentPrice();

        if (price <= pivot.s3() && vol.isVolBearish()) {
            return Optional.of(ChartPhase.CAPITULATION);
        }

        // 강한 추세 종목은 RSI/%B 과열이 정상 범위이므로, 소진 신호(약세 거래량/LH)가 함께 있어야 CLIMAX
        boolean strongTrend = mom.isStrongTrend() && pa.isUptrend();
        boolean overheated = mom.rsi() >= CLIMAX_RSI
                && (price >= pivot.r3() || bb.percentB() > PERCENT_B_OVERBOUGHT);
        boolean exhausted = !strongTrend || vol.isVolBearish() || pa.isLH();
        if (overheated && exhausted) {
            return Optional.of(ChartPhase.CLIMAX);
        }

        if (price < pivot.s2()) {
            return Optional.of(ChartPhase.STOP_LOSS);
        }
        if (pa.isLH() && pa.isLL() && vol.isVolBearish()) {
            return Optional.of(ChartPhase.DOWNTREND_CONFIRMED);
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    // 2. 고점 징후 및 차익 실현
    // ------------------------------------------------------------------
    private static Optional<ChartPhase> checkTopSignals(MarketContext ctx) {
        var pa = ctx.priceAction();
        var ma = ctx.movingAverages();
        double price = ctx.currentPrice();

        boolean strongTrend = ctx.momentum().isStrongTrend() && pa.isUptrend();

        if (!strongTrend && pa.isLH() && price < ma.ma5()) {
            return Optional.of(ChartPhase.SCALE_OUT);
        }
        if (warningScore(ctx) >= WARNING_MIN_SCORE) {
            return Optional.of(ChartPhase.WARNING_ZONE);
        }
        return Optional.empty();
    }

    /**
     * 경고 점수 (0~5). 각 항목 1점.
     * 1) 20일선 이격도 과다 (강한 추세 종목은 더 넉넉한 기준)
     * 2) RSI 과열권 진입
     * 3) 거래량 약세, 또는 이격도 과다 상태에서의 거래량 고갈
     *    (이격도가 정상인 거래량 감소는 건전한 눌림이므로 경고로 보지 않는다)
     * 4) 고점 하락(LH) 발생
     * 5) MACD 데드크로스 (강한 추세 종목은 제외)
     */
    public static List<String> warningReasons(MarketContext ctx) {
        var pa = ctx.priceAction();
        var ma = ctx.movingAverages();
        var vol = ctx.volume();
        var mom = ctx.momentum();
        var macd = ctx.macd();

        boolean strongTrend = mom.isStrongTrend() && pa.isUptrend();
        double distLimit = strongTrend ? STRONG_TREND_DIST_MA20 : WARNING_DIST_MA20;
        boolean overExtended = ma.distFromMa20() > distLimit;

        List<String> r = new ArrayList<>();
        if (overExtended)
            r.add(String.format("20일선 이격 %+.1f%%", ma.distFromMa20()));
        if (mom.rsi() >= WARNING_RSI)
            r.add(String.format("RSI %.0f", mom.rsi()));
        if (vol.isVolBearish())
            r.add("매도 거래량 우세");
        else if (overExtended && vol.isVolumeDryingUp())
            r.add("거래량 고갈");
        if (pa.isLH())
            r.add("고점 하락(LH)");
        if (!strongTrend && macd.isDeadCross())
            r.add("MACD 데드크로스");
        return r;
    }

    private static int warningScore(MarketContext ctx) {
        return warningReasons(ctx).size();
    }

    // ------------------------------------------------------------------
    // 3. 본격 상승 추세
    // ------------------------------------------------------------------
    private static Optional<ChartPhase> checkUptrend(MarketContext ctx) {
        if (ctx.momentum().isStrongTrend() && ctx.priceAction().isUptrend()) {
            return Optional.of(ChartPhase.STRONG_HOLD);
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    // 4. 조정 및 눌림목 재진입 (RE_ENTRY가 COOLDOWN보다 우선)
    // ------------------------------------------------------------------
    private static Optional<ChartPhase> checkPullback(MarketContext ctx) {
        var pa = ctx.priceAction();
        var vol = ctx.volume();
        var ma = ctx.movingAverages();
        double price = ctx.currentPrice();

        if (!pa.isMacroUptrend()) {
            return Optional.empty();
        }
        if (isNear(price, vol.vwap20()) && vol.isVolBullish()) {
            return Optional.of(ChartPhase.RE_ENTRY);
        }
        if (isNear(price, ma.ma20()) && vol.isVolumeDryingUp()) {
            return Optional.of(ChartPhase.COOLDOWN);
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    // 5. 상승 준비 및 진입 타점 (매크로 상승 구간 전용)
    //    하락 구간의 스퀴즈/횡보는 checkBearish가 처리하도록 넘긴다.
    // ------------------------------------------------------------------
    private static Optional<ChartPhase> checkSetup(MarketContext ctx) {
        var pa = ctx.priceAction();
        if (!pa.isMacroUptrend()) {
            return Optional.empty();
        }

        var vol = ctx.volume();
        var ma = ctx.movingAverages();
        var mom = ctx.momentum();
        var macd = ctx.macd();
        var bb = ctx.bollingerBand();
        var pivot = ctx.pivots().dailyPivot();
        double price = ctx.currentPrice();

        boolean notOverheated = mom.rsi() < ENTRY_MAX_RSI
                && ma.distFromMa20() <= WARNING_DIST_MA20;
        boolean noWeakness = !vol.isVolBearish() && !macd.isDeadCross();

        if (pa.isHH() && isNear(price, pivot.r1()) && notOverheated && noWeakness) {
            return Optional.of(ChartPhase.ENTRY_READY);
        }
        if (bb.isSqueeze() || (vol.isVolumeDryingUp() && pa.isSideways())) {
            return Optional.of(ChartPhase.SETUP);
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    // 6. 하락장 및 바닥 다지기 (매크로 비상승 구간만)
    // ------------------------------------------------------------------
    private static Optional<ChartPhase> checkBearish(MarketContext ctx) {
        var pa = ctx.priceAction();
        var macd = ctx.macd();

        if (pa.isMacroUptrend()) {
            return Optional.empty();
        }
        // 히스토그램 상승은 거의 매 봉 true라 노이즈가 크므로, 음수 구간에서 올라올 때만 인정
        // (histogram() 접근자 이름은 MacdResult에 맞게 조정)
        boolean momentumTurning = macd.isGoldenCross()
                || (macd.isHistogramRising() && macd.histogram() < 0);
        if (momentumTurning && pa.isHL()) {
            return Optional.of(ChartPhase.RECOVERY_WATCH);
        }
        if (pa.isUptrend()) {
            return Optional.of(ChartPhase.BEAR_RALLY);
        }
        return Optional.of(ChartPhase.DANGER_ZONE);
    }

    // ------------------------------------------------------------------
    // EntryType 세부 판정 (isValid 통과 후 호출되므로 null 체크 불필요)
    // ------------------------------------------------------------------
    private static boolean isBreakOut(MarketContext ctx) {
        var bb = ctx.bollingerBand();
        var pivot = ctx.pivots().dailyPivot();
        var vol = ctx.volume();
        var pa = ctx.priceAction();
        double price = ctx.currentPrice();

        // 저항선: R1, 유효하지 않으면 볼린저 상단
        double resistance = pivot.r1() > 0 ? pivot.r1() : bb.upper();
        boolean crossedResistance = price >= resistance;

        // 거래량: 매수세 우위 또는 20일 평균 대비 폭증
        boolean volumeSurge = vol.isVolBullish()
                || (vol.volumeMa20() > 0
                && vol.currentVolume() >= vol.volumeMa20() * BREAKOUT_VOL_RATIO);

        return crossedResistance && volumeSurge && pa.isHH();
    }

    private static boolean isReclaim(MarketContext ctx) {
        var ma = ctx.movingAverages();
        var pivot = ctx.pivots().dailyPivot();
        var macd = ctx.macd();
        double price = ctx.currentPrice();

        double keyLevel = pivot.p() > 0 ? pivot.p() : ma.ma20();
        boolean reclaimedLevel = price >= keyLevel
                && ma.distFromMa20() >= 0
                && ma.distFromMa20() <= RECLAIM_MAX_DIST_MA20;
        boolean momentumTurning = macd.isHistogramRising() || macd.isGoldenCross();

        return reclaimedLevel && momentumTurning;
    }

    private static boolean isPullback(MarketContext ctx) {
        var ma = ctx.movingAverages();
        var vol = ctx.volume();
        var pa = ctx.priceAction();

        boolean inUptrend = (pa.isUptrend() || pa.isHL()) && ma.isMa20Rising();
        boolean nearMa20Support = ma.distFromMa20() >= PULLBACK_MIN_DIST_MA20
                && ma.distFromMa20() <= PULLBACK_MAX_DIST_MA20;

        return inUptrend && nearMa20Support && vol.isVolumeDryingUp();
    }

    private static boolean isNear(double price, double level) {
        return Math.abs(price - level) / price < NEAR_TOLERANCE;
    }
}