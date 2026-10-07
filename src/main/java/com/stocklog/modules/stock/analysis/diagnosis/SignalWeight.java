package com.stocklog.modules.stock.analysis.diagnosis;

import lombok.Getter;

@Getter
public enum SignalWeight {
    MACD_CROSS(30),         // MACD 크로스: 강한 추세 전환 신호
    RSI_EXTREME(25),        // RSI 극단: 신뢰도 높은 반전 신호
    CANDLE_PATTERN(20),     // 캔들 패턴: 직접적 수급 반영
    BOLLINGER_BREAK(15),    // 볼린저 이탈: 통계적 이상 구간
    PIVOT_LEVEL(15),        // 피벗: 단기 지지/저항
    MA_CROSS(10),           // MA 이격/눌림: 보조 신호
    VOL_CONFIRM(10),        // 거래량 동반 보너스
    W_POC(10),
    W_VWAP(15);

    private final int weight;

    SignalWeight(int weight) {
        this.weight = weight;
    }

}
