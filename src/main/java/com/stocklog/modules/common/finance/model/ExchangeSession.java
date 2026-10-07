package com.stocklog.modules.common.finance.model;

import java.time.LocalTime;
import java.time.ZoneId;

/**
 * 거래소 정규장 세션. 장중 거래량 보정(projectedVolume)에서 "지금이 개장 후 몇 분째인가"를 계산하는 데 쓴다.
 * 시간대를 ZoneId로 들고 있어서 미국 서머타임은 자동으로 반영된다.
 */
public enum ExchangeSession {
    KR(ZoneId.of("Asia/Seoul"), LocalTime.of(9, 0), 390),          // 09:00 ~ 15:30
    US(ZoneId.of("America/New_York"), LocalTime.of(9, 30), 390);   // 09:30 ~ 16:00 (현지시각)

    private final ZoneId zone;
    private final LocalTime open;
    private final int minutes;

    ExchangeSession(ZoneId zone, LocalTime open, int minutes) {
        this.zone = zone;
        this.open = open;
        this.minutes = minutes;
    }

    public ZoneId zone() { return zone; }
    public LocalTime open() { return open; }
    public int minutes() { return minutes; }

    /**
     * 종목 코드로 시장을 추정한다.
     * 숫자만이면 국내, 숫자를 포함한 6자리 영숫자(KRX 신규 코드, 예: 0000D0)도 국내로 본다. 그 외는 해외.
     */
    public static ExchangeSession fromCode(String code) {
        if (code == null) return US;
        String c = code.trim().toUpperCase();
        if (c.matches("\\d+") || c.matches("(?=.*\\d)[0-9A-Z]{6}")) return KR;
        return US;
    }
}