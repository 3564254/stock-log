package com.stocklog.modules.common.finance.indicator.volatility;


import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.common.type.ChartPhase;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

import static com.stocklog.modules.common.finance.model.PhaseConst.*;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class PhaseIndicators {


    /**
     * 우선순위: 극단/리스크 -> 고점 징후 -> 상승 추세 -> 눌림 -> 셋업 -> 하락장 -> 기본(WATCH)
     */
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
    // 0. 입력 검증: 판정에 실제로 쓰는 모든 수치를 확인한다.
    //    하나라도 비정상이면 잘못된 Phase보다 INSUFFICIENT_DATA가 낫다.
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

        return allFinite(
                ctx.momentum().rsi(),
                ma.ma5(),
                ma.ma20(),
                ma.distFromMa20(),
                ctx.volume().vwap20(),
                ctx.bollingerBand().percentB(),
                pivot.s2(),
                pivot.s3(),
                pivot.r1(),
                pivot.r3()
        );
    }

    private static boolean allFinite(double... values) {
        for (double v : values) {
            if (!Double.isFinite(v)) {
                return false;
            }
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
        if (mom.rsi() >= CLIMAX_RSI
                && (price >= pivot.r3() || bb.percentB() > PERCENT_B_OVERBOUGHT)) {
            return Optional.of(ChartPhase.CLIMAX);
        }
        if (price < pivot.s2()) {
            return Optional.of(ChartPhase.STOP_LOSS);
        }
        // S2 위에서 하락 추세가 확인된 경우
        if (pa.isLH() && pa.isLL() && vol.isVolBearish()) {
            return Optional.of(ChartPhase.DOWNTREND_CONFIRMED);
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    // 2. 고점 징후 및 차익 실현
    //    WARNING_ZONE은 단일 조건이 아니라 여러 경고 신호의 합산 점수로 판정한다.
    // ------------------------------------------------------------------
    private static Optional<ChartPhase> checkTopSignals(MarketContext ctx) {
        var pa = ctx.priceAction();
        var ma = ctx.movingAverages();
        double price = ctx.currentPrice();

        boolean strongTrend = ctx.momentum().isStrongTrend() && pa.isUptrend();

        if (!strongTrend && pa.isLH() && price < ma.ma5()) {
            return Optional.of(ChartPhase.SCALE_OUT);
        }

        if (warningScore(ctx, strongTrend) >= WARNING_MIN_SCORE) {
            return Optional.of(ChartPhase.WARNING_ZONE);
        }
        return Optional.empty();
    }

    /**
     * 경고 점수 (0~5). 각 항목 1점.
     * 1) 20일선 이격도 과다 (강한 추세 종목은 더 넉넉한 기준)
     * 2) RSI 과열권 진입
     * 3) 거래량 약세 또는 고갈
     * 4) 고점 하락(LH) 발생
     * 5) MACD 데드크로스 (강한 추세 종목은 제외)
     */
    private static int warningScore(MarketContext ctx, boolean strongTrend) {
        var pa = ctx.priceAction();
        var ma = ctx.movingAverages();
        var vol = ctx.volume();
        var mom = ctx.momentum();
        var macd = ctx.macd();

        double distLimit = strongTrend
                ? STRONG_TREND_DIST_MA20
                :WARNING_DIST_MA20;

        int score = 0;
        if (ma.distFromMa20() > distLimit) score++;
        if (mom.rsi() >= WARNING_RSI) score++;
        if (vol.isVolBearish() || vol.isVolumeDryingUp()) score++;
        if (pa.isLH()) score++;
        if (!strongTrend && macd.isDeadCross()) score++;
        return score;
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
    // 4. 조정 및 눌림목 재진입
    //    VWAP과 MA20이 겹치면 RE_ENTRY(거래량 동반 반등)가 COOLDOWN보다 우선.
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
    // 5. 상승 준비 및 진입 타점
    //    ENTRY_READY: 저항선 근접만이 아니라 "과열이 아니고 약세 신호가 없는" 상태여야 한다.
    // ------------------------------------------------------------------
    private static Optional<ChartPhase> checkSetup(MarketContext ctx) {
        var pa = ctx.priceAction();
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
        if ((macd.isGoldenCross() || macd.isHistogramRising()) && pa.isHL()) {
            return Optional.of(ChartPhase.RECOVERY_WATCH);
        }
        if (pa.isUptrend()) {
            return Optional.of(ChartPhase.BEAR_RALLY);
        }
        return Optional.of(ChartPhase.DANGER_ZONE);
    }

    // ------------------------------------------------------------------
    // 유틸
    // ------------------------------------------------------------------
    private static boolean isNear(double price, double level) {
        return Math.abs(price - level) / price < NEAR_TOLERANCE;
    }
}