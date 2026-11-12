package com.stocklog.modules.stock.screening.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 홈 화면 진입 후보 카드 */
public record ScreeningResponse(
        LocalDateTime krAnalyzedAt,             // 국내 마지막 분석 시각 (KST), 없으면 null
        LocalDateTime usAnalyzedAt,             // 미국 마지막 분석 시각 (KST), 없으면 null
        int analyzedCount,                      // 분석해 둔 종목 수 (지수 ETF 포함)
        List<ScreeningCandidate> indexEtfs,     // 지수 ETF: 판정과 상관없이 전부
        List<ScreeningCandidate> candidates,    // 개별 종목 중 진입 판정이 나온 종목
        List<ScreeningCandidate> watching,      // 개별 종목 중 판정은 없지만 타점을 준비하는 국면인 종목
        List<ScreeningCandidate> trending       // 개별 종목 중 상승 추세가 이어지는 종목
) {
}
