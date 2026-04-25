package com.stocklog.domain.stock.analysis.domain.signal;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class EntrySignal {
    private final List<String> buySignals;   // 매수 고려 신호
    private final List<String> sellSignals;  // 매도 고려 신호
    private final int    buyStrength;        // 매수 신호 강도 0~100
    private final int    sellStrength;       // 매도 신호 강도 0~100
    private final double rsi;
    private final double macd;
    private final double pivot;
    private final double r1;
    private final double r2;
    private final double s1;
    private final double s2;
    private final double bollingerUpper;
    private final double bollingerLower;
    private final double currentPrice;
    private final double suggestedEntry1;
    private final double suggestedEntry2;
    private final double suggestedEntry3;
    private final double suggestedTarget1;
    private final double suggestedTarget2;
    private final double suggestedTarget3;
    private final String verdict;
}