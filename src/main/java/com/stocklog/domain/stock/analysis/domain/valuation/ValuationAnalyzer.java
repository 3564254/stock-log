package com.stocklog.domain.stock.analysis.domain.valuation;

import com.stocklog.domain.stock.dto.vo.analysis.StockSnapshot;
import com.stocklog.domain.stock.dto.vo.fundamental.financial.FinancialIndicators;
import com.stocklog.domain.stock.type.Nation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ValuationAnalyzer {

    private final ValuationBand valuationBand;

    // 총 점수: 20점 만점
    public ValuationScore analyze(StockSnapshot s, FinancialIndicators indicators) {
        if (indicators == null || indicators.isEmpty()) {
            return ValuationScore.of(0, List.of("재무 데이터 부재 — 분석 불가"));
        }

        List<String> signals = new ArrayList<>();
        double roe = (indicators.getRoe() != null) ? indicators.getRoe().doubleValue() : 0;
        int score = 0;

        // 1. ROE-PBR 정합성 평가 (12점) - 가장 높은 가중치
        score += evaluatePbrWithRoe(indicators.getPbr().doubleValue(), roe, s.getNation(), signals);

        // 2. PER 절대 평가 (6점)
        double[] perBands = valuationBand.getPerBand(s.getSector(), s.getNation());
        score += evaluatePer(indicators.getPer().doubleValue(), perBands[0], roe, signals);

        // 3. PSR 보조 평가 (2점)
        score += evaluatePsr(indicators.getPsr().doubleValue(), signals);

        return ValuationScore.of(score, signals);
    }

    private int evaluatePbrWithRoe(Double pbr, double roe, Nation nation, List<String> signals) {
        if (pbr == null || pbr <= 0) return 0;

        // 적정 PBR 계산 (Fair PBR = ROE / 요구수익률)
        // 한국 시장은 리스크 프리미엄을 반영하여 요구수익률을 높게 잡음 (12%), 미국 등은 10%
        double requiredReturn = (Nation.KR.equals(nation)) ? 12.0 : 10.0;
        double fairPbr = Math.max(0.1, roe / requiredReturn);
        double ratio = pbr / fairPbr;

        if (roe <= 0) {
            signals.add("ROE 마이너스 — PBR 가치 정당화 불가 (자산 침식 우려)");
            return 0;
        }

        if (ratio < 0.7) {
            signals.add(String.format("PBR %.2f (적정 %.2f 대비 저렴) — 수익성 대비 극저평가", pbr, fairPbr));
            return 12;
        } else if (ratio < 1.2) {
            signals.add(String.format("PBR %.2f (적정 %.2f 부근) — 현재 수익성에 부합하는 주가", pbr, fairPbr));
            return 8;
        } else if (ratio < 1.8) {
            signals.add(String.format("PBR %.2f (적정 %.2f 대비 높음) — 프리미엄 반영 중", pbr, fairPbr));
            return 3;
        }

        signals.add("PBR 과다 — ROE 대비 고평가 구간 (주의)");
        return 0;
    }

    private int evaluatePer(Double per, double lowBand, double roe, List<String> signals) {
        if (per == null || per <= 0) return 0;

        // 가치 함정 방어: PER은 낮은데 ROE가 5% 미만이면 저평가로 보지 않음
        if (per < lowBand && roe < 5.0) {
            signals.add(String.format("PER %.1f — 수치상 낮으나 낮은 수익성(ROE %.1f%%)으로 인해 저평가 매력 낮음", per, roe));
            return 1;
        }

        if (per < lowBand) {
            signals.add(String.format("PER %.1f — 섹터 하단 밴드 내 저평가", per));
            return 6;
        } else if (per < lowBand * 2.0) {
            signals.add(String.format("PER %.1f — 적정 레인지 내 위치", per));
            return 3;
        }
        return 0;
    }

    private int evaluatePsr(Double psr, List<String> signals) {
        if (psr == null) return 0;
        if (psr < 1.0) {
            signals.add(String.format("PSR %.1f — 매출액 대비 저평가 (보험 지표)", psr));
            return 2;
        }
        return 0;
    }
}