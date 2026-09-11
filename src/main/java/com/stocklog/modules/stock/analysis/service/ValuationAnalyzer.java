package com.stocklog.modules.stock.analysis.service;

import com.stocklog.modules.stock.analysis.domain.valuation.ValuationBand;
import com.stocklog.modules.stock.analysis.domain.valuation.ValuationScore;
import com.stocklog.modules.stock.domain.type.AssetType;
import com.stocklog.modules.stock.domain.type.Nation;
import com.stocklog.modules.stock.financial.dto.internal.StockFinancialDto;
import com.stocklog.modules.stock.info.dto.StockInfoDto;
import com.stocklog.modules.stock.price.dto.StockPriceDto;
import com.stocklog.modules.stock.valutation.dto.StockValuationDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ValuationAnalyzer {

    private final ValuationBand valuationBand;

    private static final int MAX_FUNDAMENTAL_SCORE = 20;
    private static final int MAX_VALUATION_SCORE = 20;
    private static final int MAX_TOTAL_SCORE = 40;


    ValuationScore analyze(StockValuationDto valuation, StockFinancialDto financial, StockInfoDto info, StockPriceDto price) {
        List<String> signals = new ArrayList<>();

        // 1. 자산 타입 확인 (ETF, ETN 등 펀드형 자산인 경우)
        if (isFundAsset(info.assetType())) {
            return analyzeEtf(financial, info, price, signals);
        }

        // 2. 일반 주식 분석 (Fundamental 20점 + Valuation 20점 = 40점 만점)
        int fundamentalScore = scoreFundamentals(financial, signals);
        int valuationScore = scoreValuation(valuation, financial, info, signals);

        return ValuationScore.of(fundamentalScore + valuationScore, signals);
    }

    // ── [1] 펀더멘탈 (기초 재무 체력) 평가 : 최대 20점 ──────────────────────
    private int scoreFundamentals(StockFinancialDto f, List<String> signals) {
        if (f == null) {
            signals.add("재무 데이터 부재 — 펀더멘탈 분석 불가");
            return 0;
        }

        int earnedScore = 0;
        int possibleScore = 0;

        Double roe = toDouble(f.roe());
        Double margin = toDouble(f.operatingMargin());
        Double growth = toDouble(f.revenueGrowth());
        Double debt = toDouble(f.debtToEquity());
        Double current = toDouble(f.currentRatio());

        // 수익성 평가 (10점)
        if (roe != null) {
            possibleScore += 5;
            if (roe >= 15) { earnedScore += 5; signals.add("고효율 자본 운용 (ROE 15%+)"); }
            else if (roe >= 8) { earnedScore += 3; signals.add("준수한 자본 수익성 (ROE 8~15%)"); }
            else if (roe < 0) { signals.add("⚠️ 자본 잠식 또는 적자 발생"); }
        }
        if (margin != null) {
            possibleScore += 5;
            if (margin >= 20) { earnedScore += 5; signals.add("압도적 시장 지배력 (영업이익률 20%+)"); }
            else if (margin >= 10) { earnedScore += 3; signals.add("안정적 수익 구조 (영업이익률 10%+)"); }
            else if (margin < 5) { signals.add("⚠️ 수익 구조 취약 (영업이익률 5% 미만)"); }
        }

        // 성장성 평가 (5점)
        if (growth != null) {
            possibleScore += 5;
            if (growth >= 20) { earnedScore += 5; signals.add("폭발적 외형 성장 (매출 성장 20%+)"); }
            else if (growth >= 10) { earnedScore += 3; signals.add("안정적 우상향 성장 (매출 성장 10%+)"); }
            else if (growth < 0) { signals.add("⚠️ 매출 역성장 발생"); }
        }

        // 안정성 평가 (5점)
        if (debt != null) {
            possibleScore += 3;
            if (debt <= 1.0) { earnedScore += 3; signals.add("재무 안전성 우수 (부채비율 100% 이하)"); }
            else if (debt > 2.0) { signals.add("⚠️ 부채 부담 높음 (부채비율 200% 초과)"); }
        }
        if (current != null) {
            possibleScore += 2;
            if (current >= 1.5) { earnedScore += 2; signals.add("충분한 유동성 확보 (유동비율 150%+)"); }
            else if (current < 1.0) { signals.add("⚠️ 단기 유동성 압박 우려"); }
        }

        return scaleScore(earnedScore, possibleScore, MAX_FUNDAMENTAL_SCORE, "펀더멘탈", signals);
    }

    // ── [2] 밸류에이션 (가격 적정성) 평가 : 최대 20점 ──────────────────────
    private int scoreValuation(StockValuationDto v, StockFinancialDto f, StockInfoDto info, List<String> signals) {
        if (v == null) {
            signals.add("밸류에이션 데이터 부재 — 가치 분석 불가");
            return 0;
        }

        int earnedScore = 0;
        int possibleScore = 0;

        Double pbr = toDouble(v.pbr());
        Double per = toDouble(v.per());
        Double peg = toDouble(v.peg());
        Double roe = toDouble(f != null ? f.roe() : null);

        // ROE-PBR 적정성 (10점)
        if (pbr != null && pbr > 0 && roe != null) {
            possibleScore += 10;
            double requiredReturn = (Nation.KR.equals(info.nation())) ? 12.0 : 10.0;
            double fairPbr = Math.max(0.1, roe / requiredReturn);
            double ratio = pbr / fairPbr;

            if (roe <= 0) {
                signals.add("ROE 마이너스 — PBR 가치 정당화 불가");
            } else if (ratio < 0.7) {
                earnedScore += 10;
                signals.add(String.format("PBR %.2f (적정 %.2f 대비 저렴) — 수익성 대비 극저평가", pbr, fairPbr));
            } else if (ratio < 1.2) {
                earnedScore += 7;
                signals.add(String.format("PBR %.2f (적정 %.2f 부근) — 현재 수익성에 부합하는 주가", pbr, fairPbr));
            } else {
                signals.add("PBR 과다 — 현재 수익성 대비 주가 부담 구간");
            }
        }

        // PER 섹터 밴드 평가 (7점)
        if (per != null && per > 0) {
            possibleScore += 7;
            double[] perBands = valuationBand.getPerBand(info.sector(), info.nation());
            double lowBand = perBands[0];

            if (per < lowBand) {
                earnedScore += 7;
                signals.add(String.format("PER %.1f — 섹터 하단 밴드 내 저평가", per));
            } else if (per < lowBand * 2.0) {
                earnedScore += 3;
                signals.add(String.format("PER %.1f — 섹터 내 적정 주가 수준", per));
            }
        }

        // PEG 성장성 평가 (3점)
        if (peg != null && peg > 0) {
            possibleScore += 3;
            if (peg < 1.0) {
                earnedScore += 3;
                signals.add(String.format("PEG %.2f — 이익 성장세 대비 저렴한 가격", peg));
            } else if (peg < 1.5) {
                earnedScore += 1;
            }
        }

        return scaleScore(earnedScore, possibleScore, MAX_VALUATION_SCORE, "밸류에이션", signals);
    }

    // ── [3] ETF/ETN 전용 평가 : 최대 40점 ────────────────────────────────
    private ValuationScore analyzeEtf(StockFinancialDto f, StockInfoDto info, StockPriceDto price, List<String> signals) {
        int earnedScore = 0;
        final int fixedPossibleScore = 40;

        signals.add(String.format("📊 [%s] 자산군 전용 분석 모델 적용", info.assetType().name()));

        // 1. 유동성 및 규모 (최대 20점 -> 더 엄격하게)
        if (price != null && price.marketCap() != null) {
            double mCap = price.marketCap().doubleValue();
            if (mCap >= 10000) { earnedScore += 20; signals.add("초대형 ETF (순자산 1조원 이상)"); }
            else if (mCap >= 3000) { earnedScore += 15; signals.add("대형 ETF (순자산 3천억원 이상)"); }
            else if (mCap >= 1000) { earnedScore += 10; signals.add("중형 ETF (순자산 1천억원 이상)"); }
            else if (mCap < 500) {
                signals.add("⚠️ 소형 ETF (순자산 500억원 미만) — 유동성 리스크 주의");
            }
        }

        // 2. 분배금 수익률 (최대 20점 -> 구간 세분화 및 기본점수 제거)
        if (f != null && f.dividendYield() != null) {
            double divYield = f.dividendYield().doubleValue();
            if (divYield >= 7.0) { earnedScore += 20; signals.add("초고배당 인컴 ETF (7%+)"); }
            else if (divYield >= 4.0) { earnedScore += 15; signals.add("고배당 ETF (4~7%)"); }
            else if (divYield >= 2.0) { earnedScore += 8; signals.add("중배당 ETF (2~4%)"); }
            else {
                // 2% 미만은 0점 처리하여 '후한 점수' 방지
                signals.add("낮은 분배율 (2% 미만) — 시세 차익 중심 자산");
            }
        }

        // scaleScore 대신 고정 분모로 계산하여 데이터 부재 시 점수 하락 유도
        int finalScore = (int) Math.round(((double) earnedScore / fixedPossibleScore) * MAX_TOTAL_SCORE);
        return ValuationScore.of(finalScore, signals);
    }

    // ── [유틸] 점수 비례 환산 로직 ──────────────────────────────────────────
    private int scaleScore(int earned, int possible, int maxTotal, String category, List<String> signals) {
        if (possible == 0) return 0;
        double ratio = (double) earned / possible;
        int finalScore = (int) Math.round(ratio * maxTotal);

        if (possible < maxTotal) {
            signals.add(String.format("※ %s 지표 일부 누락으로 가용한 데이터 기반 환산 적용", category));
        }
        return Math.min(finalScore, maxTotal);
    }

    private Double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }

    private boolean isFundAsset(AssetType type) {
        return type == AssetType.ETF || type == AssetType.ETN
                || type == AssetType.VIX_ETF || type == AssetType.VIX_ETN;
    }
}