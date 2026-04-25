package com.stocklog.domain.stock.analysis.domain.valuation;

import com.stocklog.domain.stock.dto.vo.fundamental.financial.FinancialIndicators;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class FundamentalAnalyzer {

    // 전체 만점 기준
    private static final int TOTAL_MAX_SCORE = 20;

    public ValuationScore analyze(FinancialIndicators indicators) {
        if (indicators == null || indicators.isEmpty()) {
            return ValuationScore.of(0, List.of("기초 재무 데이터 부족"));
        }

        int earnedScore = 0;
        int possibleScore = 0; // 이 기업이 '평가받을 수 있었던' 최대 점수
        List<String> signals = new ArrayList<>();

        Double roe = (indicators.getRoe() != null) ? indicators.getRoe().doubleValue() : null;
        Double margin = (indicators.getOperatingMargin() != null) ? indicators.getOperatingMargin().doubleValue() : null;
        Double growth = (indicators.getRevenueGrowth() != null) ? indicators.getRevenueGrowth().doubleValue() : null;
        Double debt = (indicators.getDebtRatio() != null) ? indicators.getDebtRatio().doubleValue() : null;
        Double current = (indicators.getCurrentRatio() != null) ? indicators.getCurrentRatio().doubleValue() : null;

        // 1. 수익성 평가 (ROE: 5점 / 영업이익률: 5점)
        if (roe != null) {
            possibleScore += 5;
            if (roe >= 15) { earnedScore += 5; signals.add("고효율 자본 운용 (ROE 15%+)"); }
            else if (roe >= 8) { earnedScore += 3; signals.add("준수한 자본 수익성 (ROE 8~15%)"); }
            else if (roe < 0) { signals.add("[주의] 자본 잠식 또는 적자 발생"); }
        }

        if (margin != null) {
            possibleScore += 5;
            if (margin >= 20) { earnedScore += 5; signals.add("압도적 시장 지배력 (영업이익률 20%+)"); }
            else if (margin >= 10) { earnedScore += 3; signals.add("안정적 수익 구조 (영업이익률 10%+)"); }
            else if (margin < 5) { signals.add("[경고] 박리다매 또는 수익 구조 취약"); }
        }

        // 2. 성장성 평가 (매출성장률: 5점)
        if (growth != null) {
            possibleScore += 5;
            if (growth >= 20) { earnedScore += 5; signals.add("폭발적 외형 성장 (매출 +20%+)"); }
            else if (growth >= 10) { earnedScore += 3; signals.add("안정적 우상향 성장 (매출 +10%+)"); }
            else if (growth >= 0) { earnedScore += 1; } // 성장 정체
            else { signals.add("[주의] 역성장 발생 (시장 점유율 하락 우려)"); }
        }

        // 3. 안정성 평가 (부채비율: 3점 / 유동비율: 2점)
        if (debt != null) {
            possibleScore += 3;
            if (debt <= 100) { earnedScore += 3; signals.add("매우 낮은 재무 리스크 (부채 100% 이하)"); }
            else if (debt < 200) { earnedScore += 1; }
            else { signals.add("[경고] 높은 부채 부담 (200% 초과)"); }
        }

        if (current != null) {
            possibleScore += 2;
            if (current >= 150) { earnedScore += 2; signals.add("충분한 단기 자금 유동성 (유동비율 150%+)"); }
            else if (current < 100) { signals.add("[주의] 단기 유동성 압박 우려"); }
        }

//        // 4. 현금흐름 질적 평가 (보너스 점수 느낌이 강하므로 기본 possibleScore에는 미포함)
//        // OCF가 당기순이익보다 크다면 추가 점수 부여 (단, 총점이 20점을 넘지 않도록 나중에 제어)
//        if (indicators.getOperatingCashFlow() != null && indicators.getNetIncome() != null) {
//            if (indicators.getOperatingCashFlow().compareTo(indicators.getNetIncome()) > 0) {
//                earnedScore += 2;
//                signals.add("영업현금흐름 > 당기순이익 — 실제 현금 유입 중심의 질적 성장");
//            }
//        }

        // ✨ 5. 점수 비례 환산 (Scaling) 로직
        int finalScore = 0;
        if (possibleScore > 0) {
            // (획득 점수 / 평가 가능 만점) * 20점 만점
            double ratio = (double) earnedScore / possibleScore;
            finalScore = (int) Math.round(ratio * TOTAL_MAX_SCORE);

            if (possibleScore < TOTAL_MAX_SCORE) {
                signals.add(String.format("일부 데이터 누락으로 유효 지표 기반 점수 비례 환산 적용 (환산 전: %d/%d)", earnedScore, possibleScore));
            }
        } else {
            signals.add("유효한 재무 지표가 없어 기본 점수(0) 부여");
        }

        // 보너스 점수 등으로 인해 20점을 초과하는 것 방지
        finalScore = Math.min(finalScore, TOTAL_MAX_SCORE);

        return ValuationScore.of(finalScore, signals);
    }
}