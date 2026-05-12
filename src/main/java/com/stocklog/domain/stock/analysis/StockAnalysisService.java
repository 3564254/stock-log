package com.stocklog.domain.stock.analysis;


import com.stocklog.domain.stock.analysis.domain.momentum.MomentumAnalyzer;
import com.stocklog.domain.stock.analysis.domain.momentum.MomentumScore;
import com.stocklog.domain.stock.analysis.domain.signal.EntrySignalAnalyzer;
import com.stocklog.domain.stock.analysis.domain.signal.vo.EntrySignal;
import com.stocklog.domain.stock.analysis.domain.technical.TechnicalAnalyzer;
import com.stocklog.domain.stock.analysis.domain.technical.TechnicalScore;
import com.stocklog.domain.stock.analysis.domain.valuation.ValuationAnalyzer;
import com.stocklog.domain.stock.analysis.domain.valuation.ValuationScore;
import com.stocklog.domain.stock.analysis.result.StockAnalysisResult;
import com.stocklog.domain.application.dto.stock.StockSnapshot;
import com.stocklog.domain.stock.common.type.AssetType;
import com.stocklog.domain.stock.info.dto.internal.StockInfoDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockAnalysisService {
    private final MomentumAnalyzer momentumAnalyzer;
    private final TechnicalAnalyzer technicalAnalyzer;
    private final ValuationAnalyzer valuationAnalyzer;
    private final EntrySignalAnalyzer entrySignalAnalyzer;

    public StockAnalysisResult getAnalysisResult(StockSnapshot snapshot) {
        String code = snapshot.info().code();
        String name = snapshot.info().name();

        log.info("[Analysis] 종목 분석 시작 - {}({})", name, code);

        // 1. 각 도메인별 분석 실행 및 로그
        MomentumScore momentumScore = momentumAnalyzer.analyze(snapshot.dailyCharts(), snapshot.price());
        log.info("[Analysis] 모멘텀 분석 완료 - Score: {}, Signals: {}", momentumScore.getScore(), momentumScore.getSignals().size());

        TechnicalScore technicalScore = technicalAnalyzer.analyze(snapshot.dailyCharts(), snapshot.weeklyCharts());
        log.info("[Analysis] 기술적 분석 완료 - Score: {}, Signals: {}", technicalScore.getScore(), technicalScore.getSignals().size());

        ValuationScore valuationScore = valuationAnalyzer.analyze(snapshot.valuation(), snapshot.financial(), snapshot.info(), snapshot.price());
        log.info("[Analysis] 밸류에이션 분석 완료 - Score: {}, Signals: {}", valuationScore.getScore(), valuationScore.getSignals().size());

        EntrySignal entrySignal = entrySignalAnalyzer.analyze(snapshot.dailyCharts(), snapshot.weeklyCharts());
        log.info("[Analysis] 진입 신호 분석 완료 - Result: {}", entrySignal.verdict());

        // 2. 종합 점수 및 데이터 가공
        int totalScore = getScore(momentumScore, technicalScore, valuationScore);
        String grade = toGrade(totalScore);
        boolean isEtf = isEtf(snapshot.info());
        List<String> allSignals = getAllSignals(momentumScore, technicalScore, valuationScore);

        log.info("[Analysis] 분석 완료 - {}({}): 최종 점수 {}점, 등급 {}, 시그널 총 {}개",
                name, code, totalScore, grade, allSignals.size());

        return StockAnalysisResult.builder()
                .code(code)
                .score(totalScore)
                .grade(grade)
                .isEtf(isEtf)
                .signals(allSignals)
                .valuation(valuationScore)
                .momentum(momentumScore)
                .technical(technicalScore)
                .entrySignal(entrySignal)
                .build();
    }

    private List<String> getAllSignals(MomentumScore m, TechnicalScore t, ValuationScore v) {
        List<String> signals = new ArrayList<>();
        signals.addAll(m.getSignals());
        signals.addAll(t.getSignals());
        signals.addAll(v.getSignals());
        return signals;
    }

    private boolean isEtf(StockInfoDto infoDto) {
        boolean isEtf = !infoDto.assetType().equals(AssetType.COMMON_STOCK);
        if (isEtf) {
            log.info("[Analysis] 해당 종목은 ETF/ETN 자산군으로 분류되었습니다.");
        }
        return isEtf;
    }

    private int getScore(MomentumScore m, TechnicalScore t, ValuationScore v) {
        int mScore = m.getScore();
        int tScore = t.getScore();
        int vScore = v.getScore();

        int total = mScore + tScore + vScore;
        // 기존 로그보다 가독성을 높임
        log.info("[Analysis] 점수 합산 상세 -> 모멘텀: {}, 기술: {}, 가치: {} | 총점: {}",
                mScore, tScore, vScore, total);
        return total;
    }

    private String toGrade(int score) {
        String grade;
        if (score >= 80) grade = "S";
        else if (score >= 65) grade = "A";
        else if (score >= 48) grade = "B";
        else if (score >= 30) grade = "C";
        else grade = "D";

        return grade;
    }
}
