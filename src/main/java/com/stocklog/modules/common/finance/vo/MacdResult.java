package com.stocklog.modules.common.finance.vo;

import com.stocklog.modules.common.finance.indicator.MathUtils;
import com.stocklog.modules.common.finance.indicator.MomentumIndicators;
import lombok.Builder;

import java.util.List;

@Builder
public record MacdResult(
        double macd,
        double signal,
        double histogram,
        boolean isGoldenCross,
        boolean isDeadCross,
        boolean isHistogramRising
) {}
