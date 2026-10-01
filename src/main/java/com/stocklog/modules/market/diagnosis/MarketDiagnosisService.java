package com.stocklog.modules.market.diagnosis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocklog.modules.ai.AiProvider;
import com.stocklog.modules.market.analysis.dto.EngineInput;
import com.stocklog.modules.market.analysis.dto.MarketRegimeDto;
import com.stocklog.modules.market.domain.type.MarketRegimeType;
import com.stocklog.modules.market.domain.type.MacroRegimeType;
import com.stocklog.modules.market.domain.type.RiskAppetiteType;
import com.stocklog.modules.market.domain.type.TrendDirection;
import com.stocklog.modules.market.news.dto.NewsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketDiagnosisService {

    private final AiProvider aiProvider;
    private final MarketDiagnosisRepository diagnosisRepository;
    private final MarketDiagnosisHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    public MarketDiagnosisDto getMarketDiagnosisDto() {
        return MarketDiagnosisDto.from(diagnosisRepository.findTopByOrderByDateDesc()
                .orElseThrow(() -> new IllegalStateException("시장 진단 데이터가 없습니다.")));
    }

    @Transactional
    public MarketDiagnosisDto getMarketDiagnosisDto(EngineInput input, MarketRegimeDto regimeDto, List<NewsDto> newsList) {
        LocalDate today = LocalDate.now();
        Optional<MarketDiagnosis> todayDiagnosis = diagnosisRepository.findByDate(today);

        if (todayDiagnosis.isPresent()) {
            MarketDiagnosis diagnosis = todayDiagnosis.get();
            if (isCommentError(diagnosis.getComment())) {
                log.info("[AI 분석 중 오류 발생] 재시도 진행 - 기존 코멘트 업데이트");
                String newComment = fetchMarketDiagnosisComment(input, newsList);
                diagnosis.updateComment(newComment);
                return MarketDiagnosisDto.from(diagnosis);
            }
            return MarketDiagnosisDto.from(diagnosis);
        }

        return createNewDiagnosisAndHistory(input, regimeDto, newsList, today);
    }

    private MarketDiagnosisDto createNewDiagnosisAndHistory(EngineInput input, MarketRegimeDto regimeDto, List<NewsDto> newsList, LocalDate today) {
        // [A] 그룹별 서브 스코어 및 종합 스코어 계산 (당일 현황 Diagnosis 엔티티용)
        double priceScore = calculateAverage(input.trend().score(), input.momentum().score(), input.breadth().score());
        double liquidityScore = calculateAverage(input.rates().score(), input.liquidity().score(), input.macro().score());
        double sectorScore = calculateAverage(input.sector().score(), input.defensive().score());
        double crossAssetScore = calculateAverage(input.crossAsset().score(), input.divergence().score());

        double compositeScore = calculateAverage(priceScore, liquidityScore, sectorScore, crossAssetScore);

        // [B] Enum 매핑
        MarketRegimeType regime = mapToRegimeType(regimeDto.regime());
        RiskAppetiteType risk = mapToRiskType(regimeDto.risk());

        // [C] 전일 데이터 조회하여 추세(Delta), 연속 일수(Streak) 계산
        MarketDiagnosisHistory prevHistory = historyRepository.findTopByOrderByDateDesc().orElse(null);
        double scoreChange1D = (prevHistory != null) ? (compositeScore - prevHistory.getCompositeScore()) : 0.0;
        int streakDays = (prevHistory != null && prevHistory.getRegime() == regime) ? prevHistory.getRegimeStreakDays() + 1 : 1;
        TrendDirection trend = determineTrend(scoreChange1D);

        List<MarketDiagnosisHistory> histories = historyRepository.findAllByOrderByDateDesc(PageRequest.of(4, 1));

        double scoreChange5D = histories.stream()
                .findFirst()
                .map(h -> compositeScore - h.getCompositeScore())
                .orElse(0.0);

        // [D] JSON 원본 변환
        String rawJson = "";
        try {
            rawJson = objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            log.warn("EngineInput JSON 직렬화 실패", e);
        }

        // [E] AI 코멘트 생성
        String comment = fetchMarketDiagnosisComment(input, newsList);

        // ─── [F] DB 저장 1: 시계열 History 추가 (신규 구조 및 반올림 반영) ───
        MarketDiagnosisHistory newHistory = MarketDiagnosisHistory.builder()
                .date(today)
                .compositeScore(round(compositeScore))
                .regime(regime)
                .regimeStreakDays(streakDays)
                .risk(risk)
                .confidence(regimeDto.confidence())
                .trendScore(round(input.trend().score()))
                .momentumScore(round(input.momentum().score()))
                .breadthScore(round(input.breadth().score()))
                .ratesScore(round(input.rates().score()))
                .liquidityScore(round(input.liquidity().score()))
                .macroScore(round(input.macro().score()))
                .creditScore(round(input.credit().score()))
                .sectorScore(round(input.sector().score()))
                .defensiveScore(round(input.defensive().score()))
                .crossAssetScore(round(input.crossAsset().score()))
                .macroRegime(mapToMacroRegimeType(extractJsonString(rawJson, "macro", "macroRegime")))
                .macroNarrative(extractJsonString(rawJson, "crossAsset", "macroNarrative"))
                .hasBreadthDivergence(extractJsonBoolean(rawJson, "divergence", "hasBreadthDivergence"))
                .rawEngineInputJson(rawJson)
                .build();

        historyRepository.save(newHistory);

        // ─── [G] DB 저장 2: 오늘의 최신 상태 저장 (당일 스냅샷) ───
        MarketDiagnosis newDiagnosis = MarketDiagnosis.builder()
                .date(today)
                .compositeScore(round(compositeScore))
                .scoreChange1D(round(scoreChange1D))
                .scoreChange5D(round(scoreChange5D))
                .trendDirection(trend)
                .regimeStreakDays(streakDays)
                .regime(regime)
                .risk(risk)
                .regimeDescription(regimeDto.regimeDescription())
                .confidence(regimeDto.confidence())
                .marketPriceScore(priceScore)
                .macroLiquidityScore(liquidityScore)
                .sectorScore(sectorScore)
                .crossAssetScore(crossAssetScore)
                .comment(comment)
                .build();

        diagnosisRepository.save(newDiagnosis);

        return MarketDiagnosisDto.from(newDiagnosis);
    }

    private String fetchMarketDiagnosisComment(EngineInput input, List<NewsDto> newsList) {
        return aiProvider.getMarketAnalysis(input, newsList);
    }

    // ─── [유틸리티 메서드] ───

    private boolean isCommentError(String comment) {
        return comment == null || comment.isEmpty() || comment.equals("AI 분석 중 오류 발생");
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double calculateAverage(double... scores) {
        double sum = 0;
        for (double score : scores) sum += score;
        double avg = scores.length > 0 ? sum / scores.length : 0.0;
        return round(avg); // 계산 직후 바로 반올림 적용하여 반환
    }

    private TrendDirection determineTrend(double delta1D) {
        if (delta1D >= 5.0) return TrendDirection.IMPROVING;
        if (delta1D <= -5.0) return TrendDirection.DETERIORATING;
        return TrendDirection.STABLE;
    }

    private MarketRegimeType mapToRegimeType(String regimeStr) {
        try {
            if (regimeStr == null) return MarketRegimeType.NEUTRAL;
            return MarketRegimeType.valueOf(regimeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("알 수 없는 Regime 상태값: {}", regimeStr);
            return MarketRegimeType.NEUTRAL;
        }
    }

    private RiskAppetiteType mapToRiskType(String riskStr) {
        try {
            if (riskStr == null) return RiskAppetiteType.NEUTRAL;
            return RiskAppetiteType.valueOf(riskStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("알 수 없는 Risk 상태값: {}", riskStr);
            return RiskAppetiteType.NEUTRAL;
        }
    }

    private MacroRegimeType mapToMacroRegimeType(String macroRegimeStr) {
        try {
            if (macroRegimeStr == null) return MacroRegimeType.UNKNOWN;
            return MacroRegimeType.valueOf(macroRegimeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("알 수 없는 Macro Regime 상태값: {}", macroRegimeStr);
            return MacroRegimeType.UNKNOWN;
        }
    }

    private String extractJsonString(String json, String analyzerKey, String fieldName) {
        try {
            var node = objectMapper.readTree(json);
            var target = node.path(analyzerKey).path("rawDetails").path(fieldName);
            return target.isMissingNode() || target.isNull() ? null : target.asText();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean extractJsonBoolean(String json, String analyzerKey, String fieldName) {
        try {
            var node = objectMapper.readTree(json);
            return node.path(analyzerKey).path("rawDetails").path(fieldName).asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }
}