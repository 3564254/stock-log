package com.stocklog.domain.portfolio.entity;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public enum PortfolioGrade {
    S(90, "S", "완벽한 포트폴리오입니다! 분산과 수익의 밸런스가 매우 뛰어납니다.", "#FFD700"), // Gold
    A(70, "A", "우수한 포트폴리오입니다. 특정 종목의 비중을 조금 더 조절하면 안정성이 높아집니다.", "#C0C0C0"), // Silver
    B(50, "B", "무난한 구성입니다. 손실 중인 종목의 섹터가 겹치지 않는지 확인해보세요.", "#CD7F32"), // Bronze
    C(0,  "C", "포트폴리오 재구성이 필요해 보입니다. 전문가의 조언이나 리밸런싱을 고려해보세요.", "#A9A9A9"); // DarkGray

    private final int minScore;
    private final String gradeName;
    private final String insightMessage;
    private final String colorCode; // UI 적용을 위한 HEX 코드

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

