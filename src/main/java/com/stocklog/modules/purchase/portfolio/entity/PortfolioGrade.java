package com.stocklog.modules.purchase.portfolio.entity;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public enum PortfolioGrade {
    S(90, "S", "완벽한 포트폴리오임 강력한 주도주 탑승과 적절한 현금 버퍼를 통해 리스크가 완벽히 헤지되어 있음", "#FFD700"),
    A(70, "A", "우수한 자산 배분임 다만 특정 섹터의 비중이 50%를 초과하는지 확인하여 집중도 리스크를 관리할 필요가 있음", "#C0C0C0"),
    B(50, "B", "시장 수익률을 추종하는 무난한 구성임 하락장에 대비할 수 있는 현금(예수금) 비중이 충분한지 점검 요망", "#CD7F32"),
    C(0,  "C", "포트폴리오의 극단적인 쏠림 또는 심각한 트렌드 소외 현상이 감지됨 진단 리포트를 참고하여 대대적인 리밸런싱 권장", "#A9A9A9");

    private final int minScore;
    private final String gradeName;
    private final String insightMessage;
    private final String colorCode;

    PortfolioGrade(int minScore, String gradeName, String insightMessage, String colorCode) {
        this.minScore = minScore;
        this.gradeName = gradeName;
        this.insightMessage = insightMessage;
        this.colorCode = colorCode;
    }

    public static PortfolioGrade of(BigDecimal totalScore) {
        if (totalScore.compareTo(new BigDecimal("90")) >= 0) return S;
        if (totalScore.compareTo(new BigDecimal("70")) >= 0) return A;
        if (totalScore.compareTo(new BigDecimal("50")) >= 0) return B;
        return C;
    }
}