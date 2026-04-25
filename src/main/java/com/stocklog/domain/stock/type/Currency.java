package com.stocklog.domain.stock.type;

public enum Currency {
    KRW,USD,UNKNOWN;

    public static Currency fromString(String s) {
        try {
            return Currency.valueOf(s.trim().toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isDomestic() {
        return this == KRW;
    }
}
