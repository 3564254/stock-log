package com.stocklog.modules.stock.analysis.signal.level;

import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.common.finance.dto.CandleChartDto;
import com.stocklog.modules.common.finance.dto.SimpleChartDto;
import com.stocklog.modules.common.finance.util.MathUtils;

import java.util.List;

/**
 * 입력 어댑터: MarketContext에서 가격 레벨과 ATR을 뽑는 유일한 지점 (구 TradingSignalAnalyzer.Inputs).
 * 아직 접근자를 모르는 값은 NaN으로 두며, 그 레벨은 자동으로 무시된다.
 * (임시 위치: 다음 단계에서 signal/level 로 이동)
 */
public record SignalInputs(double price, double open, double high, double low, double atr,
                           double recentHigh, double recentLow,
                           double ma20, double ma50, double vwap20, double poc,
                           double bbLower, double bbUpper,
                           double pivotP, double s1, double s2, double s3, double r1) {

    private static final int LEVEL_HIGH_LOOKBACK = 60; // 돌파 기준 고점 구간 (오늘 제외)
    private static final int LEVEL_LOW_LOOKBACK  = 20; // 스윙 저점 구간 (오늘 제외)

    public static SignalInputs from(MarketContext ctx) {
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

        return new SignalInputs(
                ctx.currentPrice(),
                open, high, low,
                ctx.momentum().atr(),
                recentHigh, recentLow,
                ma.ma20(), ma.ma50(),
                ctx.volume().vwap20(), ctx.volume().pocPrice(),
                ctx.bollingerBand().lower(), ctx.bollingerBand().upper(),
                pv.p(), pv.s1(), pv.s2(), pv.s3(), pv.r1());
    }

    /** 몸통/꼬리는 전일 종가가 아니라 당일 시가-종가로 계산해야 한다. 반전 캔들 판정에 사용. */
    public double lowerWickRatio() {
        double range = high - low;
        if (!Double.isFinite(range) || range <= 0) return 0;
        return (Math.min(open, price) - low) / range;
    }

    public double upperWickRatio() {
        double range = high - low;
        if (!Double.isFinite(range) || range <= 0) return 0;
        return (high - Math.max(open, price)) / range;
    }
}
