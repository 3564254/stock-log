package com.stocklog.modules.market.analysis.dto;

import lombok.Getter;


public record TotalResult(
        EngineInput engineInput,
        MarketRegimeDto marketRegimeDto,
        java.time.LocalDate dataDate       // 분석에 쓴 S&P 500 최신 종가 날짜 (화면 '기준일' 표시용)
) {
}
