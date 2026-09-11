package com.stocklog.modules.stock.domain.type;

public enum Nation {
    KR,
    US,
    UNKNOWN;

    public static Nation fromString(String n) {
        if (n == null || n.isBlank()) return UNKNOWN;

        String input = n.trim().toUpperCase();

        // 1. KIS 3글자 코드를 2글자 상수로 매핑
        String mappedInput = switch (input) {
            case "KOR" -> "KR";
            case "USA" -> "US";
            default -> input;
        };

        // 2. 변환된 값으로 Enum 상수 찾기
        try {
            return Nation.valueOf(mappedInput);
        } catch (IllegalArgumentException e) {
            return UNKNOWN; // null 대신 UNKNOWN을 반환하는 것이 더 안전합니다.
        }
    }


    public boolean isDomestic() {
        return this == KR;
    }
}
