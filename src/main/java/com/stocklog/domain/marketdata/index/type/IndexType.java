package com.stocklog.domain.marketdata.index.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum IndexType {

    // ── 1. 주가 지수 (Stock Market Indices) ──
    SP500("SP500", "S&P 500"),
    NASDAQ("NASDAQCOM", "나스닥 종합지수"),
    DOW_JONES("DJIA", "다우존스 산업평균지수"),

    PHLX_SEMI("NASDAQSOX", "필라델피아 반도체 지수"),
    WILSHIRE_5000("WILL5000INDFC", "윌셔 5000 전체시장지수"),

    // ── 2. 시장 변동성 (Market Volatility) ──
    VIX("VIXCLS", "VIX (변동성 지수)"),

    // ── 3. 채권 및 금리 (Interest Rates & Bonds) ──
    US_TREASURY_10Y("DGS10", "미국 10년물 국채 금리"),
    US_TREASURY_2Y("DGS2", "미국 2년물 국채 금리"),
    T10Y2Y("T10Y2Y", "10년물-2년물 국채 금리차"),
    US_FED_FUNDS_RATE("FEDFUNDS", "미국 연방기금금리 (기준금리)"),

    // ── 4. 원자재 (Commodities) ──
    WTI_CRUDE_OIL("DCOILWTICO", "WTI 원유"),
    BRENT_CRUDE_OIL("DCOILBRENTEU", "브렌트유"),
    GOLD("GOLDAMGBD228NLBM", "금 (런던 고시가)"),

    // ── 5. 환율 (Exchange Rates) ──
    USD_KRW("DEXKOUS", "원/달러 환율"),
    DOLLAR_INDEX("DTWEXBGS", "무역 가중 달러 인덱스"),

    // ── 6. 거시 경제 지표 (Macroeconomic Indicators) ──
    US_CPI("CPIAUCSL", "미국 소비자물가지수 (CPI)"),
    US_UNEMPLOYMENT_RATE("UNRATE", "미국 실업률"),
    // 추가: GDP (버핏 지수 계산용)
    US_GDP("GDPC1", "미국 실질 GDP"),
    // 추가: 통화량 (유동성 확인용)
    US_M2("M2SL", "미국 M2 통화량");


    private final String seriesId;


    private final String displayName;

    public static IndexType fromSeriesId(String seriesId) {
        for (IndexType type : values()) {
            if (type.getSeriesId().equalsIgnoreCase(seriesId)) {
                return type;
            }
        }
        throw new IllegalArgumentException("지원하지 않는 지수 ID 입니다: " + seriesId);
    }
}