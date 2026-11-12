package com.stocklog.modules.purchase.portfolio.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record PortfolioAnalysisResponse (
    // 계산 지표
    BigDecimal hhi,
    BigDecimal winRate,
    BigDecimal totalScore,

    // 등급 정보
    String grade,
    String insightMessage,
    String gradeColor,

    // 차트 데이터 (Chart.js 연동)
    List<String> chartLabels,
    List<BigDecimal> chartData,

    BigDecimal totalAssets
){

}
