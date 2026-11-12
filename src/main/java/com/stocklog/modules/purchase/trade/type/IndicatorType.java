package com.stocklog.modules.purchase.trade.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum IndicatorType {

    // 수치형 지표 (Numeric) : 연산자(>, <) 및 기준값 필수
    STOCK_SCORE("종합 점수 (0~100)", true),          // 밸류 + 모멘텀 + 기술
    VALUATION_SCORE("밸류 점수 (0~40)", true),       // 재무 체력 20 + 가격 적정성 20
    MOMENTUM_SCORE("모멘텀 점수 (0~30)", true),      // 52주 위치 · 3개월 수익률 · RSI · 거래량
    TECHNICAL_SCORE("기술 점수 (0~30)", true),       // 이동평균 배열 · MA20×MA60 크로스 · 변동성
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

    // 상태/이벤트 지표 (Boolean) : 기준값 불필요 (선택 자체가 조건)
    UPTREND("정배열 상태 (단기>스윙>중기)", false),
    MACRO_UPTREND("장기 상승 추세 (현재가 > 50일선)", false),
    BOLLINGER_SQUEEZE("볼린저 밴드 극도 응축 (폭발 임박)", false),
    VOLUME_BULLISH("매수 거래량 폭발 (전일 대비 급증+양봉)", false),
    NEW_HIGH_5D("단기 고점 갱신 (최근 5일 최고가 돌파)", false),
    HIGHER_LOW("저점 방어 (전일 대비 저점 상승)", false),
    MACD_GOLDEN_CROSS("MACD 골든 크로스 발생", false);

    private final String description;

    private final boolean isNumeric;

    /** 차트(MarketContext)가 아니라 종목 점수 계산이 필요한 지표 */
    public boolean isScore() {
        return this == STOCK_SCORE || this == VALUATION_SCORE || this == MOMENTUM_SCORE || this == TECHNICAL_SCORE;
    }
}