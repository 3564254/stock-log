package com.stocklog.modules.common.type;

public enum Nation {
    KR,
    US,
    UNKNOWN;

    public static Nation fromString(String n) {
        if (n == null || n.isBlank()) return UNKNOWN;

        String input = n.trim().toUpperCase();

        // KIS 3글자 코드를 2글자 상수로 매핑
        String mappedInput = switch (input) {
            case "KOR" -> "KR";
            case "USA" -> "US";
            default -> input;
        };

        // 변환된 값으로 Enum 상수 찾기
        try {
            return Nation.valueOf(mappedInput);
        } catch (IllegalArgumentException e) {
            return UNKNOWN;        }
    }

    public boolean isDomestic() {
        return this == KR;
    }
}
