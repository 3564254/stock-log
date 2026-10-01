package com.stocklog.modules.common.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChartDataType {
    STOCK("개별 종목", true),       // OHLCV 완비 (일반주식, ETF 등)
    INDEX("시장 지수", false),     // 종가 위주 (코스피, 나스닥 등)

    UNKNOWN("알 수 없음",false);

    private final String description;
    private final boolean supportsFullCandle;

    public boolean isStock() {
        return this == STOCK;
    }

    public boolean isIndex() {
        return this == INDEX;
    }
}
