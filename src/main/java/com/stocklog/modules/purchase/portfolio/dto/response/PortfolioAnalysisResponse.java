package com.stocklog.modules.purchase.portfolio.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record PortfolioAnalysisResponse (
    // 1. 계산 지표
    BigDecimal hhi,
    BigDecimal winRate,
    BigDecimal totalScore,

    // 2. 등급 정보 (PortfolioGrade 기반)
    String grade,
    String insightMessage,
    String gradeColor,

    // 3. 차트 데이터 (Chart.js 연동)
    List<String> chartLabels,
    List<BigDecimal> chartData,

    BigDecimal totalAssets
){

}
