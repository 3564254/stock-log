package com.stocklog.domain.marketdata.universe.entity;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
public enum Universe {
    // 🚀 성장 및 기술 (Risk-On)
    TECHNOLOGY("기술 섹터", List.of(
            new TickerInfo("XLK", "기술"),
            new TickerInfo("SOXX", "반도체"),
            new TickerInfo("XLY", "자유소비재"),
            new TickerInfo("IGV", "소프트웨어")
    )),

    // 🛡️ 가치 및 방어 (Risk-Off)
    DEFENSIVE("방어 섹터", List.of(
            new TickerInfo("XLP", "필수소비재"),
            new TickerInfo("XLV", "헬스케어"),
            new TickerInfo("XLU", "유틸리티")
    )),

    // 🏗️ 산업 및 금융
    INFRASTRUCTURE("인프라/금융", List.of(
            new TickerInfo("XLI", "산업재"),
            new TickerInfo("XLF", "금융"),
            new TickerInfo("XAR", "우주항공 및 방산")
    )),

    // 🛢️ 원자재 및 에너지
    COMMODITY("원자재/에너지", List.of(
            new TickerInfo("XLE", "에너지"),
            new TickerInfo("XME", "금속 및 광산"),
            new TickerInfo("OILK", "원유"),
            new TickerInfo("XOP", "석유 및 가스 탐사")
    ));

    private final String description;
    private final List<TickerInfo> tickers; // 단순 String에서 TickerInfo 객체로 변경

    Universe(String description, List<TickerInfo> tickers) {
        this.description = description;
        this.tickers = tickers;
    }

    // ==========================================
    // 📌 내부 클래스: 티커와 섹터 이름을 함께 보관
    // (Java 16 이상이라면 record TickerInfo(String code, String sectorName) {} 로 더 짧게 쓸 수 있습니다)
    // ==========================================
    @Getter
    public static class TickerInfo {
        private final String code;
        private final String sectorName;

        public TickerInfo(String code, String sectorName) {
            this.code = code;
            this.sectorName = sectorName;
        }
    }

    // ==========================================
    // 📌 유틸리티 메서드
    // ==========================================

    /**
     * 1. 모든 티커 코드만 추출 (하드코딩 제거)
     */
    public static List<String> getAllCodes() {
        return Arrays.stream(Universe.values())
                .flatMap(universe -> universe.getTickers().stream())
                .map(TickerInfo::getCode)
                .toList();
    }

    /**
     * 2. 특정 티커 코드로 상위 Universe(대분류) 찾기
     */
    public static Universe getUniverse(String code) {
        if (code == null || code.isBlank()) return null;

        String upperCode = code.toUpperCase();
        return Arrays.stream(Universe.values())
                .filter(universe -> universe.getTickers().stream()
                        .anyMatch(tickerInfo -> tickerInfo.getCode().equals(upperCode)))
                .findFirst()
                .orElse(null);
    }

    /**
     * 3. 특정 티커 코드로 세부 섹터 이름 찾기 (예: "SOXX" -> "반도체")
     */
    public static String getSectorName(String code) {
        if (code == null || code.isBlank()) return "알 수 없음";

        String upperCode = code.toUpperCase();
        return Arrays.stream(Universe.values())
                .flatMap(universe -> universe.getTickers().stream())
                .filter(tickerInfo -> tickerInfo.getCode().equals(upperCode))
                .map(TickerInfo::getSectorName)
                .findFirst()
                .orElse("알 수 없음");
    }

}