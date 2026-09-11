package com.stocklog.modules.stock.analysis.domain.signal.vo;

import lombok.Builder;

@Builder
public record MacdResult(
        double macd,
        double signal,
        double histogram,
        boolean isGoldenCross,
        boolean isDeadCross,
        boolean isHistogramRising
) {

}
