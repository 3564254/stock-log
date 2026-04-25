package com.stocklog.domain.stock.dto.response;

import com.stocklog.domain.stock.analysis.result.StockAnalysisResult;
import lombok.Builder;

@Builder
public record StockAnalysisPageResponse(
        StockDetailResponse stockDetail,         // 주가 및 기본 정보
        StockFinancialsResponse financials,      // 재무제표 데이터
        StockAnalysisResult analysisResult       // 순수 분석 결과 (점수, 시그널)
) {
}