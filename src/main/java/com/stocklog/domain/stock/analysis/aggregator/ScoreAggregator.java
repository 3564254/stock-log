package com.stocklog.domain.stock.analysis.aggregator;

import com.stocklog.domain.stock.analysis.result.StockAnalysisResult;
import com.stocklog.domain.stock.analysis.domain.momentum.MomentumScore;
import com.stocklog.domain.stock.analysis.domain.technical.TechnicalScore;
import com.stocklog.domain.stock.analysis.domain.valuation.ValuationScore;
import com.stocklog.domain.stock.analysis.domain.signal.EntrySignal;
import com.stocklog.domain.stock.dto.response.StockFinancialsResponse; // ✨ import 추가
import com.stocklog.domain.stock.dto.vo.analysis.StockSnapshot;
import com.stocklog.domain.stock.type.Nation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ScoreAggregator {

    // 일반 주식 비중
    private static final double W_VALUATION = 0.40;
    private static final double W_MOMENTUM  = 0.30;
    private static final double W_TECHNICAL = 0.30;

    // ETF 비중 (밸류에이션 제외)
    private static final double ETF_W_MOMENTUM = 0.50;
    private static final double ETF_W_TECHNICAL = 0.50;

    public StockAnalysisResult aggregate(StockSnapshot snapshot,
                                         ValuationScore valuation,
                                         MomentumScore momentum,
                                         TechnicalScore technical,
                                         EntrySignal entrySignal,
                                         StockFinancialsResponse finData,
                                         boolean isEtf) {

        double raw;
        if (isEtf) {
            // ETF는 모멘텀과 기술적 분석에 5:5 비중 부여
            raw = (momentum.getScore() / 30.0 * 100) * ETF_W_MOMENTUM
                    + (technical.getScore() / 30.0 * 100) * ETF_W_TECHNICAL;
        } else {
            // 일반 주식 비중 적용
            raw = (valuation.getScore() / 40.0 * 100) * W_VALUATION
                    + (momentum.getScore()  / 30.0 * 100) * W_MOMENTUM
                    + (technical.getScore() / 30.0 * 100) * W_TECHNICAL;
        }

        int penalty = calcRiskPenalty(snapshot);
        int finalScore = Math.max(0, Math.min(100, (int) Math.round(raw) - penalty));

        List<String> allSignals = new ArrayList<>();
        if (isEtf) allSignals.add("ETF 종목 — 가격 추세 기반 분석");

        allSignals.addAll(valuation.getSignals());
        allSignals.addAll(momentum.getSignals());
        allSignals.addAll(technical.getSignals());

        return StockAnalysisResult.builder()
                .code(snapshot.getCode())
                .score(finalScore)
                .grade(toGrade(finalScore, penalty))
                .isEtf(isEtf)
                .signals(allSignals)
                .valuation(valuation)
                .momentum(momentum)
                .technical(technical)
                .entrySignal(entrySignal)
                .financials(finData)
                .build();
    }

    private int calcRiskPenalty(StockSnapshot s) {
        if (Nation.KR.equals(s.getNation()) && "Y".equals(s.getIsAdminItem())) {
            return 35;
        }
        return 0;
    }

    private String toGrade(int score, int penalty) {
        if (penalty >= 35) return "D";
        if (score >= 80)   return "S";
        if (score >= 65)   return "A";
        if (score >= 48)   return "B";
        if (score >= 30)   return "C";
        return "D";
    }
}