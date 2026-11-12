package com.stocklog.modules.market.analysis.diagnosis;

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
    private final com.stocklog.modules.market.weekly.MarketWeeklyService weeklyService;

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

            // 오늘 이미 만든 진단이라도, 그 뒤 새 종가가 들어와 엔진 결과(국면·문구·신뢰도)가 바뀌었으면 다시 만든다
            if (isStale(diagnosis, regimeDto)) {
                log.info("[시장 진단] 엔진 결과가 바뀌어 오늘 진단을 다시 만듭니다: {} → {}",
                        diagnosis.getRegimeDescription(), regimeDto.regimeDescription());
                diagnosisRepository.delete(diagnosis);
                historyRepository.findByDate(today).ifPresent(historyRepository::delete);
                // 날짜 unique 제약: 새로 넣기 전에 삭제를 먼저 DB에 반영한다
                diagnosisRepository.flush();
                historyRepository.flush();
                return createNewDiagnosisAndHistory(input, regimeDto, newsList, today);
            }

            // 코멘트가 실패였으면 다시 시도하되, 접속할 때마다가 아니라 30분에 한 번만
            if (isCommentError(diagnosis.getComment()) && retryAllowed(diagnosis)) {
                String newComment = fetchMarketDiagnosisComment(input, newsList);
                diagnosis.updateComment(newComment);
                log.warn("[AI 분석 중 오류 발생] 재시도 진행: {}", newComment.substring(0, Math.min(10, newComment.length())) + "... ");
                return MarketDiagnosisDto.from(diagnosis);
            }
            return MarketDiagnosisDto.from(diagnosis);
        }

        return createNewDiagnosisAndHistory(input, regimeDto, newsList, today);
    }

    private boolean isStale(MarketDiagnosis saved, MarketRegimeDto fresh) {
        if (fresh == null) return false;
        return saved.getRegime() != mapToRegimeType(fresh.regime())
                || saved.getRisk() != mapToRiskType(fresh.risk())
                || !java.util.Objects.equals(saved.getRegimeDescription(), fresh.regimeDescription())
                || Math.abs(saved.getConfidence() - fresh.confidence()) >= 5;   // 소수점 흔들림으로 AI 코멘트를 매번 새로 만들지 않게
    }

    private MarketDiagnosisDto createNewDiagnosisAndHistory(EngineInput input, MarketRegimeDto regimeDto, List<NewsDto> newsList, LocalDate today) {
        // A 그룹별 서브 스코어 및 종합 스코어 계산
        double priceScore = calculateAverage(input.trend().score(), input.momentum().score(), input.breadth().score());
        double liquidityScore = calculateAverage(input.rates().score(), input.liquidity().score(), input.macro().score());
        double sectorScore = calculateAverage(input.sector().score(), input.defensive().score());
        double crossAssetScore = calculateAverage(input.crossAsset().score(), input.divergence().score());

        // 0 이하만 나오는 괴리 점수가 섞여 늘 낮게 치우쳤고, 권장 현금 비중이 쓰는 '시장 점수'와도 달랐다
        double compositeScore = com.stocklog.modules.market.analysis.service.result.MarketRegimeEngine.coreScore(com.stocklog.modules.market.analysis.service.result.EngineScores.of(input));

        // B Enum 매핑
        MarketRegimeType regime = mapToRegimeType(regimeDto.regime());
        RiskAppetiteType risk = mapToRiskType(regimeDto.risk());

        // C 전일 데이터 조회하여 추세(Delta), 연속 일수(Streak) 계산
        MarketDiagnosisHistory prevHistory = historyRepository.findTopByOrderByDateDesc().orElse(null);
        double scoreChange1D = (prevHistory != null) ? (compositeScore - coreOf(prevHistory)) : 0.0;
        int streakDays = (prevHistory != null && prevHistory.getRegime() == regime) ? prevHistory.getRegimeStreakDays() + 1 : 1;
        TrendDirection trend = determineTrend(scoreChange1D);

        List<MarketDiagnosisHistory> histories = historyRepository.findAllByOrderByDateDesc(PageRequest.of(4, 1));

        double scoreChange5D = histories.stream()
                .findFirst()
                .map(h -> compositeScore - coreOf(h))
                .orElse(0.0);

        // D JSON 원본 변환
        String rawJson = "";
        try {
            rawJson = objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            log.warn("EngineInput JSON 직렬화 실패", e);
        }

        // E AI 코멘트 생성
        String comment = fetchMarketDiagnosisComment(input, newsList);

        // F DB 저장 1: 시계열 History 추가
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

        // G DB 저장 2: 오늘의 최신 상태 저장 (당일 스냅샷)
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

        newDiagnosis.updateComment(comment);   // 코멘트 받은 시각 기록
        if (isCommentError(comment)) lastCommentRetry = java.time.Instant.now();
        diagnosisRepository.save(newDiagnosis);

        return MarketDiagnosisDto.from(newDiagnosis);
    }

    private String fetchMarketDiagnosisComment(EngineInput input, List<NewsDto> newsList) {
        Optional<MarketDiagnosisHistory> history = historyRepository.findTopByOrderByDateDesc();
        MarketDiagnosisHistoryDto historyDto;
        if (history.isPresent()) {
            historyDto = MarketDiagnosisHistoryDto.from(history.get());
        } else {
            historyDto = MarketDiagnosisHistoryDto.createFallback();
        }
        return aiProvider.getMarketAnalysis(input, newsList, historyDto, weeklyRules());
    }

    /** AI 시황 브리핑에 넘길 앱 규칙과 이번 주 사실 (기본 성향 기준) */
    private String weeklyRules() {
        try {
            var w = weeklyService.weekly();
            StringBuilder sb = new StringBuilder();
            for (var g : w.regimes()) {
                if (!g.known()) continue;
                String nm = "US".equals(g.market()) ? "미국" : "한국";
                sb.append(String.format("- %s 시장(%s): %s · 52주 고점 대비 %.1f%%%n", nm, g.indexName(), g.weak() ? "40주선 아래 (약세)" : "40주선 위", g.ddFromHigh()));
            }
            sb.append("- 앱 규칙 1 (새로 살 때 크기): 그 시장 지수가 40주선 위면 평소 크기(기본 성향 종목당 계좌 위험 1%), 아래면 절반(안정 성향은 보류). ")
              .append("백테스트에서 낙폭을 줄이는 보험이었고 수익을 늘리지는 않았음\n");
            sb.append("- 앱 규칙 2 (보유 종목): 시장 상태와 상관없이 종목마다 정한 기준선(손절·수익 보호선)이 깨질 때만 정리. 시장 분위기로 미리 팔지 않음\n");
            sb.append(String.format("- 앱 규칙 3 (미국 모멘텀 상위 20%% 분기 교체 전략, 앱에서 유일하게 검증된 전략): 다음 교체일 %s (D-%d). 그 전에는 할 일 없음, 시장 상태와 무관%n",
                    w.nextRebalance(), Math.max(0, w.rebalanceDays())));
            java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("America/New_York"));
            var soon = w.events().stream().filter(e -> !e.date().isAfter(today.plusDays(7))).toList();
            if (soon.isEmpty()) sb.append("- 이번 주(7일) 큰 발표: 없음 (확인된 일정 기준)\n");
            else for (var e : soon) sb.append(String.format("- 이번 주 발표: %s %s (%s). 발표 전후 갭이 나면 손절가보다 낮게 체결될 수 있음%n",
                    e.date(), e.name(), e.date().getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.KOREAN)));
            var sec = w.sectors().stream().filter(s -> s.known() && s.ret6m() != null).toList();
            if (sec.size() >= 6) {
                long up = sec.stream().filter(s -> Boolean.TRUE.equals(s.above40w())).count();
                String top = sec.subList(0, 3).stream().map(s -> String.format("%s %+.0f%%", s.sectorKo(), s.ret6m() * 100)).collect(java.util.stream.Collectors.joining(", "));
                String bot = sec.subList(sec.size() - 3, sec.size()).stream().map(s -> String.format("%s %+.0f%%", s.sectorKo(), s.ret6m() * 100)).collect(java.util.stream.Collectors.joining(", "));
                sb.append(String.format("- 미국 업종 ETF 6개월: 강한 쪽 %s / 약한 쪽 %s · 40주선 위 업종 %d/%d (참고, 판정에 안 씀)%n", top, bot, up, sec.size()));
            }
            return sb.toString().strip();
        } catch (Exception e) {
            log.warn("[시장 진단] 이번 주 규칙 맥락 실패: {}", e.getMessage());
            return "";
        }
    }

    // 유틸리티 메서드

    /** 지난 기록의 종합 점수를 지금 정의(핵심 지표 가중합)로 다시 계산한다 */
    private double coreOf(MarketDiagnosisHistory h) {
        try {
            var s = com.stocklog.modules.market.analysis.service.result.EngineScores.fromJson(objectMapper, h.getRawEngineInputJson());
            if (s != null && !s.isEmpty()) return com.stocklog.modules.market.analysis.service.result.MarketRegimeEngine.coreScore(s);
        } catch (Exception ignored) { /** 저장된 값으로 */ }
        return h.getCompositeScore();
    }

    private boolean isCommentError(String comment) {
        return comment == null || comment.isEmpty() || comment.equals(AiProvider.ERROR_MESSAGE) || comment.equals(AiProvider.QUOTA_MESSAGE);
    }

    private volatile java.time.Instant lastCommentRetry = java.time.Instant.EPOCH;

    /** 마지막 시도 + 30분이 지났고, 이 서버에서 방금 시도하지 않았으면 */
    private synchronized boolean retryAllowed(MarketDiagnosis d) {
        if (d.getCommentAt() != null && d.getCommentAt().plusMinutes(30).isAfter(java.time.LocalDateTime.now())) return false;
        java.time.Instant now = java.time.Instant.now();
        if (lastCommentRetry.plusSeconds(30 * 60).isAfter(now)) return false;
        lastCommentRetry = now;
        return true;
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
            return MacroRegimeType.valueOf(macroRegimeStr.trim().toUpperCase());
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