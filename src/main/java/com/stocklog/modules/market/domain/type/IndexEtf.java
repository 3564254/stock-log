package com.stocklog.modules.market.domain.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum IndexEtf {

    // ==========================================
    // 1. Broad Market (시가총액 가중 대표 지수)
    // ==========================================
    SPY("SPY", "S&P 500 ETF (시가총액 가중)"),
    QQQ("QQQ", "Invesco QQQ Trust (나스닥 100 시가총액 가중)"),
    DIA("DIA", "SPDR Dow Jones Industrial Average ETF"),

    // ==========================================
    // 2. Market Breadth (시장 확산도 측정용 대체 ETF)
    // ==========================================
    RSP("RSP", "Invesco S&P 500 Equal Weight ETF (동일 가중)"),
    QQEW("QQEW", "First Trust NASDAQ-100 Equal Weighted ETF (동일 가중)"),
    IWM("IWM", "iShares Russell 2000 ETF (중소형주)"),

    // ==========================================
    // 3. (Optional) 향후 Analyzer 확장을 위한 예비 티커
    // ==========================================
    // Sector / Defensive
    XLU("XLU", "Utilities Select Sector SPDR Fund (방어주/유틸리티)"),
    XLP("XLP", "Consumer Staples Select Sector SPDR Fund (방어주/필수소비재)"),
    SOXX("SOXX", "iShares Semiconductor ETF (반도체/주도주)"),

    // Credit / Risk
    HYG("HYG", "iShares iBoxx $ High Yield Corporate Bond ETF (하이일드 크레딧)");

    private final String code;
    private final String description;

    /**
     * 티커 심볼로 Enum 객체를 찾는 유틸리티 메서드
     */
    public static IndexEtf fromTicker(String code) {
        for (IndexEtf etf : values()) {
            if (etf.getCode().equalsIgnoreCase(code)) {
                return etf;
            }
        }
        throw new IllegalArgumentException("Unknown ETF code: " + code);
    }
}
