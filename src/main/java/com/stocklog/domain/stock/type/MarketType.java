package com.stocklog.domain.stock.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum MarketType {
    // [국내]
    KOSPI("02", "J"),
    KOSDAQ("03", "Q"),

    // [해외]
    NASD("512", "NAS"), // NASDAQ
    NYSE("513", "NYS"), // New York
    AMEX("529", "AMS"), // AMEX (KIS 기준 AMS)

    UNKNOWN("", "");

    private final String kisCode;      // KIS API 내부 코드 (예: 512)
    private final String exchangeCode; // 거래소 단축 코드 (예: NAS)

    public static MarketType from(String s) {
        if (s == null || s.isBlank()) return UNKNOWN;

        String upperS = s.trim().toUpperCase();

        return Arrays.stream(MarketType.values())
                .filter(m -> m.name().equals(upperS) ||
                        m.kisCode.equals(upperS) ||
                        m.exchangeCode.equals(upperS))
                .findFirst()
                .orElse(UNKNOWN);
    }

    public boolean isDomestic() {
        return this == KOSPI || this == KOSDAQ;
    }

    public boolean isOverseas() {
        return !isDomestic() && this != UNKNOWN;
    }

    public String getOverseasPriceCode() {
        return this.kisCode;
    }
}