package com.stocklog.modules.common.finance.model;

import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.common.finance.series.PriceSeries;
import com.stocklog.modules.common.finance.util.MathUtils;
import lombok.Builder;

import java.util.List;

import static com.stocklog.modules.common.finance.indicator.trend.TrendIndicators.calcDistance;


@Builder
public record MovingAverages(
        double ma5,
        double ma20,
        double ma50,
        double ma5Yesterday,
        double ma20Yesterday,
        boolean isMa5Rising,
        boolean isMa20Rising,
        double distFromMa5,
        double distFromMa20,
        double distFromMa50
)
{

    public static MovingAverages createFallback() {
        return MovingAverages.builder()
                .ma5(0).ma20(0).ma50(0).ma5Yesterday(0).ma20Yesterday(0)
                .isMa5Rising(false).isMa20Rising(false)
                .distFromMa5(0).distFromMa20(0).distFromMa50(0)
                .build();
    }

    public static MovingAverages of(List<Double> closes, int lastIdx, double currentPrice) {
        return getMovingAverages(closes, lastIdx, currentPrice);
    }

    public static MovingAverages of(PriceSeries priceSeries) {
        return getMovingAverages(priceSeries.closes(), priceSeries.lastIdx(), priceSeries.currentPrice());
    }


    private static MovingAverages getMovingAverages(List<Double> closes, int lastIdx, double currentPrice) {
        if (MarketContext.invalidate(closes)) return createFallback();

        // 1. 당일 이동평균선 (SMA)
        double ma5 = MathUtils.sma(closes, lastIdx, 5);
        double ma20 = MathUtils.sma(closes, lastIdx, 20);
        double ma50 = MathUtils.sma(closes, lastIdx, 50);

        // 2. 전일 이동평균선 (데이터가 최소 1일치 전 인덱스가 있을 때)
        int prevIdx = lastIdx - 1;
        double ma5Yesterday = prevIdx >= 0 ? MathUtils.sma(closes, prevIdx, 5) : 0;
        double ma20Yesterday = prevIdx >= 0 ? MathUtils.sma(closes, prevIdx, 20) : 0;

        // 3. 상승 추세 여부 (당일 MA > 전일 MA)
        boolean isMa5Rising = ma5Yesterday > 0 && ma5 > ma5Yesterday;
        boolean isMa20Rising = ma20Yesterday > 0 && ma20 > ma20Yesterday;

        // 4. 이격도 (Disparity / Distance Percentage)
        // 공식: ((현재가 - MA) / MA) * 100
        double distFromMa5 = calcDistance(currentPrice, ma5);
        double distFromMa20 = calcDistance(currentPrice, ma20);
        double distFromMa50 = calcDistance(currentPrice, ma50);

        return MovingAverages.builder()
                .ma5(ma5).ma20(ma20).ma50(ma50)
                .ma5Yesterday(ma5Yesterday).ma20Yesterday(ma20Yesterday)
                .isMa5Rising(isMa5Rising).isMa20Rising(isMa20Rising)
                .distFromMa5(distFromMa5).distFromMa20(distFromMa20).distFromMa50(distFromMa50)
                .build();
    }


}
