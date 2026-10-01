package com.stocklog.modules.common.finance.indicator.priceaction;

import com.stocklog.modules.common.finance.model.MovingAverages;
import com.stocklog.modules.common.finance.model.PriceAction;
import com.stocklog.modules.common.finance.series.PriceSeries;
import com.stocklog.modules.common.finance.util.MathUtils;

import java.util.List;

public final class PriceActionIndicators {

    private PriceActionIndicators() {}

    public static PriceAction calcPriceAction(PriceSeries priceSeries, MovingAverages ma, double adx) {
        List<Double> highs = priceSeries.highs();
        List<Double> lows = priceSeries.lows();
        int lastIdx = priceSeries.lastIdx();
        int lookbackPeriod = 5;

        double recentHigh5 = MathUtils.highest(highs, lastIdx - 1, lookbackPeriod);
        double recentLow5 = MathUtils.lowest(lows, lastIdx - 1, lookbackPeriod);

        boolean isHH = highs.get(lastIdx) > recentHigh5;
        boolean isLL = lows.get(lastIdx) < recentLow5;
        boolean isHL = lows.get(lastIdx) > lows.get(lastIdx - 1) && lows.get(lastIdx - 1) > recentLow5;
        boolean isLH = highs.get(lastIdx) < highs.get(lastIdx - 1) && highs.get(lastIdx - 1) < recentHigh5;

        boolean isMacroUptrend = !Double.isNaN(ma.ma50()) && ma.ma50() > 0 && priceSeries.currentPrice() > ma.ma50();
        boolean isUptrend = (ma.ma5() > ma.ma20()) && (ma.ma20() > ma.ma50()) && ma.isMa20Rising();
        boolean isSideways = !isUptrend && adx > 0 && adx < 20;

        return PriceAction.builder()
                .isHH(isHH).isHL(isHL).isLH(isLH).isLL(isLL)
                .isMacroUptrend(isMacroUptrend).isUptrend(isUptrend).isSideways(isSideways)
                .build();
    }
}