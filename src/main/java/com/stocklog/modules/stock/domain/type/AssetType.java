package com.stocklog.modules.stock.domain.type;

import lombok.Getter;
import java.util.Arrays;

@Getter
public enum AssetType {
    COMMON_STOCK("", "일반주식"),
    ETF("001", "ETF"),
    ETN("002", "ETN"),
    ETC("003", "ETC(원자재)"),
    OTHERS("004", "기타(리츠, 뮤추얼펀드)"),
    VIX_ETF("005", "VIX 기반 ETF"),
    VIX_ETN("006", "VIX 기반 ETN"),
    UNKNOWN("000", "미분류");

    private final String code;
    private final String description;

    AssetType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static AssetType fromCode(String code) {
        // 값이 null이거나 빈 문자열이면 일반 주식으로 간주
        if (code == null || code.trim().isEmpty()) {
            return COMMON_STOCK;
        }

        return Arrays.stream(AssetType.values())
                .filter(type -> type.code.equals(code))
                .findFirst()
                .orElse(COMMON_STOCK); // 정의되지 않은 코드도 일반 주식으로 취급하거나 별도 에러 처리
    }

    public static AssetType fromKoreanEtfCode(String etfDvsnCd) {
        if (etfDvsnCd.equals("N") || etfDvsnCd == null || etfDvsnCd.trim().isEmpty()) {
            return COMMON_STOCK;
        }

        return switch (etfDvsnCd) {
            case "00" -> COMMON_STOCK;
            case "01" -> ETF;
            case "02" -> ETN;
            default -> UNKNOWN;
        };
    }
}