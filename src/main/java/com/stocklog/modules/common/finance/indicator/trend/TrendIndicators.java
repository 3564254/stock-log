package com.stocklog.modules.common.finance.indicator.trend;

import com.stocklog.modules.common.finance.model.MovingAverages;
import com.stocklog.modules.common.finance.series.PriceSeries;
import com.stocklog.modules.common.finance.util.MathUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TrendIndicators {


    /** 이격도 계산 (Disparity) */
    public static double calcDistance(double currentPrice, double ma) {
        if (ma == 0) return 0.0;
        return ((currentPrice - ma) / ma) * 100.0;
    }

    public static MovingAverages calcMovingAverages(PriceSeries priceSeries) {
        List<Double> closes = priceSeries.closes();
        int lastIdx = priceSeries.lastIdx();
        double currentPrice = priceSeries.currentPrice();

        // 1. 당일 이동평균선 (SMA)
        double ma5 = MathUtils.sma(closes, lastIdx, 5);
        double ma20 = MathUtils.sma(closes, lastIdx, 20);
        double ma50 = MathUtils.sma(closes, lastIdx, 50);

        // 2. 전일 이동평균선
        int prevIdx = lastIdx - 1;
        double ma5Yesterday = prevIdx >= 0 ? MathUtils.sma(closes, prevIdx, 5) : 0;
        double ma20Yesterday = prevIdx >= 0 ? MathUtils.sma(closes, prevIdx, 20) : 0;

        // 3. 상승 추세 여부
        boolean isMa5Rising = ma5Yesterday > 0 && ma5 > ma5Yesterday;
        boolean isMa20Rising = ma20Yesterday > 0 && ma20 > ma20Yesterday;

        // 4. 이격도
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