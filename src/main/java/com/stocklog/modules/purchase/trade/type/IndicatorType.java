package com.stocklog.modules.purchase.trade.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum IndicatorType {

    // ==========================================
    // 1. 수치형 지표 (Numeric) : 연산자(>, <) 및 기준값 필수
    // ==========================================
    STOCK_SCORE("점수",true),
    MA5("5일 이동평균선 (단기)", true),
    MA20("20일 이동평균선 (스윙)", true),
    MA50("50일 이동평균선 (중기)", true),
    DIST_MA5("5일선 이격도 (%)", true),
    DIST_MA20("20일선 이격도 (%)", true),
    RSI("RSI (상대강도지수)", true),
    ADX("ADX (추세강도)", true),
    ATR("ATR (변동성)", true),
    BAND_WIDTH("볼린저 대역폭 (%)", true),
    VOLUME("현재 거래량", true),

    // ==========================================
    // 2. 상태/이벤트 지표 (Boolean) : 기준값 불필요 (선택 자체가 조건)
    // ==========================================
    UPTREND("정배열 상태 (단기>스윙>중기)", false),
    MACRO_UPTREND("장기 상승 추세 (현재가 > 50일선)", false),
    BOLLINGER_SQUEEZE("볼린저 밴드 극도 응축 (폭발 임박)", false),
    VOLUME_BULLISH("매수 거래량 폭발 (전일 대비 급증+양봉)", false),
    NEW_HIGH_5D("단기 고점 갱신 (최근 5일 최고가 돌파)", false),
    HIGHER_LOW("저점 방어 (전일 대비 저점 상승)", false),
    MACD_GOLDEN_CROSS("MACD 골든 크로스 발생", false);

    private final String description;

    private final boolean isNumeric;
}