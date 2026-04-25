package com.stocklog.domain.stock.analysis.domain.valuation;


import com.stocklog.domain.stock.dto.vo.analysis.StockSnapshot;
import com.stocklog.domain.stock.dto.vo.fundamental.financial.FinancialIndicators;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CorporateValueAnalyzer {

    private final ValuationAnalyzer valuationAnalyzer;
    private final FundamentalAnalyzer fundamentalAnalyzer;

    public ValuationScore analyze(StockSnapshot snapshot, FinancialIndicators indicators, boolean isEtf) {

        // ETF는 가치/재무 평가를 아예 생략하므로 0점 처리 후 바로 리턴합니다.
        if (isEtf) {
            return ValuationScore.of(0, List.of("ETF — 밸류에이션 및 펀더멘털 분석 미적용"));
        }

        // 1. 시장 가치 평가 (20점)
        ValuationScore valScore = valuationAnalyzer.analyze(snapshot, indicators);

        // 2. 기업 체력 평가 (20점)
        ValuationScore fundScore = fundamentalAnalyzer.analyze(indicators);

        // 3. 점수 합산 (총 40점)
        int totalScore = valScore.getScore() + fundScore.getScore();

        // 4. 시그널(분석 코멘트) 합산
        List<String> combinedSignals = new ArrayList<>();
        combinedSignals.addAll(valScore.getSignals());
        combinedSignals.addAll(fundScore.getSignals());

        // 5. 합쳐진 하나의 결과물을 리턴
        return ValuationScore.of(totalScore, combinedSignals);
    }
}
