package com.stocklog.infra.gemini.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocklog.infra.gemini.dto.GeminiRequest;
import com.stocklog.modules.ai.AiException;
import com.stocklog.modules.ai.AiProvider;
import com.stocklog.modules.market.analysis.dto.EngineInput;
import com.stocklog.modules.market.analysis.diagnosis.MarketDiagnosisHistoryDto;
import com.stocklog.modules.market.news.dto.NewsDto;
import com.stocklog.modules.purchase.portfolio.analysis.dto.PortfolioAnalysisResult;
import com.stocklog.modules.purchase.portfolio.dto.response.PortfolioAnalysisResponse;
import com.stocklog.modules.common.type.ChartPhase;
import com.stocklog.modules.stock.analysis.signal.trading.TradingSignalResult;
import com.stocklog.modules.stock.analysis.result.StockAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiAiProvider implements AiProvider {

    @Value("${gemini.api.base-url}")
    private String baseUrl;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.models.flash}")
    private String flashModel;

    @Value("${gemini.api.models.pro}")
    private String proModel;

    private final RestTemplate restTemplate;

    @Override
    public String getStockAnalysis(StockAnalysisResult analysis) {
        String code = analysis.getCode();
        TradingSignalResult signal = analysis.getTradingSignalResult();
        boolean hasSignal = signal != null
                && signal.analysis() != null
                && signal.analysis().phase() != ChartPhase.INSUFFICIENT_DATA;

        TradingSignalResult.SignalStrength strength = (hasSignal && signal.strength() != null)
                ? signal.strength()
                : TradingSignalResult.SignalStrength.empty();

        String prompt = String.format("""
                당신은 여의도 탑티어 증권사의 프랍 트레이딩(Prop Trading) 퀀트 수석 애널리스트야. 당신의 임무는 주어진 '%s' 종목의 데이터를 분석해, 투자자가 당장 '어떤 행동(Action)'을 취해야 하는지 명확하고 구체적인 지침을 내리는 거야.

                [분석 데이터]
                종합: %d/100 (%s)
                가치: %d/40 (%s) | 수급: %d/30 (%s) | 차트: %d/30 (%s)
                차트 국면: %s
                타점: %s
                리스크: %s
                신호: 매수강도 %d%%(%s), 매도강도 %d%%(%s), 관찰 포인트(%s)
                엔진 판정: %s

                [🚨 절대 엄수 지시사항 - 위반 시 해고]
                1. 행동 중심(Action-Oriented) 서술: "그래서 지금 어떻게 해야 하는가?"에 대한 명확한 답을 제시해 (예: 현재가 추격 매수 금지, X원까지 눌림목 대기 후 3분할 진입, 보유자 영역이므로 신규 진입 보류 등)
                2. 앵무새 금지: 가치 X점, 수급 Y점 같은 단순 수치 나열 절대 금지 (숫자는 제시된 타점 가격만 노출할 것)
                3. 데이터의 이면 해석: 점수의 괴리(예: 가치↑ 차트↓)를 통해 세력의 의도나 시장 심리를 읽어내어 '행동 지침의 근거'로만 사용해
                4. 엔진 판정 준수: 엔진 판정과 모순되는 행동 지침 금지 (예: 판정이 눌림 대기·확인 대기·추격 위험·손익비 부족·진입 계획 없음인데 즉시 매수를 권하는 서술 금지)
                5. 데이터 외 가격 금지: 타점에 N/A 또는 '없음'으로 표시된 가격은 임의로 만들어내지 말고, 가격 없이 조건(지지 확인, 반등 확인 등)으로만 서술
                6. 문체 및 포맷:
                   - 인사말과 서론 절대 생략
                   - 마크다운 볼드체(**) 등 모든 기호 사용 절대 금지
                   - 모든 문장의 끝은 반드시 간결한 명사형으로 종결 (~함, ~임, ~요망, ~필요, ~대기, ~권장 등)
                   - 문장 끝에 마침표(.) 등 문장 부호 절대 금지

                아래 4가지 소제목을 사용하여 밀도 있고 날카롭게 작성해 (각 2~3문장).

                📊 현재 국면 및 시장 심리 (가치/수급/차트 데이터의 충돌과 차트 국면을 분석해 현재 주가 위치의 본질을 진단)
                🔮 단기 주가 시나리오 (매수/매도 강도와 엔진 판정을 융합하여 예상되는 상승/하락 경로 제시)
                ⚔️ 구체적 액션 플랜 및 타점 (제시된 진입 구간/목표가를 바탕으로 '언제, 어떻게, 얼마나' 사야 하는지 명확한 행동 지침 제안)
                🛡️ 리스크 대응 매뉴얼 (손절가 이탈 시 뇌동매매를 막기 위한 기계적인 하방 방어 행동 수칙 제시)

                ※ 마지막 줄에는 반드시 다음 문구를 그대로 출력할 것: ※ 본 분석은 퀀트 데이터 기반의 참고용이며, 투자 권유 아님
                """,
                code,
                analysis.getScore(),
                analysis.getGrade(),
                analysis.getValuation().getScore(),
                joinOrNone(analysis.getValuation().getSignals()),
                analysis.getMomentum().getScore(),
                joinOrNone(analysis.getMomentum().getSignals()),
                analysis.getTechnical().getScore(),
                joinOrNone(analysis.getTechnical().getSignals()),
                hasSignal ? describePhase(signal) : "차트 데이터 부족으로 판정 불가",
                hasSignal ? describePlan(code, signal) : "산출 불가",
                hasSignal ? describeRisk(signal) : "산출 불가",
                strength.buyStrength(), joinOrNone(strength.buySignals()),
                strength.sellStrength(), joinOrNone(strength.sellSignals()),
                joinOrNone(strength.watchSignals()),
                hasSignal ? verdictLabel(signal.verdict()) : "데이터 부족"
        );

        return callGemini(prompt, flashModel);
    }

    // 종목 분석 프롬프트용 포맷 헬퍼
    private static String joinOrNone(Collection<String> items) {
        return (items == null || items.isEmpty()) ? "없음" : String.join(", ", items);
    }

    private static boolean isKrCode(String code) {
        return code != null && code.matches("\\d+");
    }

    /** 0 이하 또는 비정상 값은 "N/A". 국내 종목은 원, 해외 종목은 달러로 표기한다 */
    private static String price(String code, double v) {
        if (!Double.isFinite(v) || v <= 0) return "N/A";
        return isKrCode(code) ? String.format("%,.0f원", v) : String.format("$%,.2f", v);
    }

    private static String describePhase(TradingSignalResult s) {
        ChartPhase phase = s.analysis().phase();
        return phase.getTitle() + " (" + phase.getDescription() + ")";
    }

    /** 정식 타점이 있으면 진입 구간·손절·목표·손익비를, 참고 가격대뿐이면 그 사실을 함께 밝힌다 */
    private static String describePlan(String code, TradingSignalResult s) {
        var plan = s.entryPlan();
        var target = s.priceTarget();
        if (plan == null || target == null || plan.zoneHigh() <= 0) {
            return "산출 가능한 진입 구간 없음";
        }

        boolean formal = !"NONE".equals(plan.type().name());
        StringBuilder sb = new StringBuilder();
        sb.append(formal ? plan.type().getTitle() + " 진입 구간 " : "참고 가격대(정식 타점 아님) ")
                .append(price(code, plan.zoneLow())).append(" ~ ").append(price(code, plan.zoneHigh()))
                .append(" / 손절가 ").append(price(code, target.stopLoss()))
                .append(" / 1차 목표가 ")
                .append(target.primaryTarget() > 0 ? price(code, target.primaryTarget()) : "뚜렷한 저항 구간 없음");
        if (target.secondaryTarget() > 0) {
            sb.append(" / 2차 목표가 ").append(price(code, target.secondaryTarget()));
        }
        sb.append(" / 손익비 ").append(plan.riskReward() > 0 ? String.format("%.1f", plan.riskReward()) : "산출 불가");
        if (formal) {
            sb.append(" / 당일 양봉 ").append(plan.triggerConfirmed() ? "예" : "아니오").append(" (양봉은 반등 확정 신호 아님)");
        }
        return sb.toString();
    }

    private static String describeRisk(TradingSignalResult s) {
        var risk = s.risk();
        if (risk == null || risk.tradeRiskPercent() <= 0) return "산출 불가";
        String weight = risk.recommendedWeight() > 0
                ? String.format("%.1f%%", risk.recommendedWeight())
                : "진입 실행 가능 판정 시에만 산출";
        return String.format("손절까지 거리 %.1f%%, 권장 비중 %s (기본 성향: 계좌 위험 1%%, 종목 상한 20%% 기준)", risk.tradeRiskPercent(), weight);
    }

    /** TradingSignalAnalyzer.Verdict 이름을 AI가 그대로 인용해도 되는 설명으로 바꾼다 */
    private static String verdictLabel(String verdict) {
        if (verdict == null) return "데이터 부족";
        if (verdict.startsWith("FALLBACK")) return "데이터 부족";
        return switch (verdict) {
            case "ACTIONABLE"    -> "기술적 조건 충족 (구간 안, 당일 양봉, 손익비 통과 · 반등 확정 아님, 수익 우위 미검증)";
            case "WAIT_FOR_ZONE" -> "눌림 구간 대기 (현재가가 진입 구간 위)";
            case "WAIT_TRIGGER"  -> "구간 안이나 당일 양봉 전";
            case "CHASE_RISK"    -> "추격 위험 (돌파·탈환 구간 이탈)";
            case "RR_TOO_LOW"    -> "손익비 부족";
            case "NO_SETUP"      -> "진입 계획 없음";
            case "BOUNDARY_WATCH" -> "경계 확인 중 (오늘 진입 구간 경계를 오르내림, 조금 더 지켜볼 자리)";
            default              -> verdict;
        };
    }

    @Override
    public String getPortfolioAnalysis(PortfolioAnalysisResult analysisResult) {
        PortfolioAnalysisResponse ai = analysisResult.analysis();
        boolean hasAi = (ai != null);

        String holdingsSummary = analysisResult.myStocks().stream()
                .map(s -> String.format("- %s (%s)", s.korName(), s.sector() != null ? s.sector() : "미분류"))
                .collect(Collectors.joining("\n"));

        String prompt = String.format("""
                당신은 월스트리트 출신의 1%% 자산관리 전문가이자 냉철한 포트폴리오 전략가야. 고객의 포트폴리오 데이터를 바탕으로 자산 구성의 맹점을 짚어주고, 수익성을 극대화할 수 있는 전략 리포트를 작성해.

                [데이터]
                - 분석 지표: 등급 %s (%d점) / HHI 지수 %s / 투자 승률 %.1f%%
                - AI 진단: %s
                - 보유 종목:
                %s

                [지시사항]
                주어진 데이터를 바탕으로 아래 4가지 소제목을 반드시 사용하여 전문적이고 날카롭게(뼈때리는 조언 포함) 작성해. (각 항목당 2~3문장)
                - ** 기호 사용 금지.
                - 자신 설명 금지. 바로 본론으로 들어가기.
                - 문장의 끝은 반드시 명사형으로 종결할 것 (~함, ~임, ~요망, ~필요 등)
                - 문장 끝에 온점(.)을 절대 찍지 말 것

                📈 포트폴리오 진단 총평 (현재 수익률과 등급을 바탕으로 한 자산 운용 상태의 냉정한 평가)
                ⚖️ 자산 배분 및 집중도 분석 (HHI 지수와 보유 종목 구성을 분석하여 분산 투자 정도와 위험 요소 지적)
                🚀 수익성 개선 전략 (승률과 종목 구성을 바탕으로 기대 수익을 높이기 위한 포지션 조절 제안)
                🛡️ 리스크 관리 가이드 (현재 자산 구성에서 가장 취약한 지점과 시장 변동성 대응 방안)

                ※ 마지막 줄에는 반드시 '※ 본 분석은 데이터 기반의 참고용이며, 투자 권유 아님' 이라고 명시할 것
                """,
                hasAi ? ai.grade() : "N/A",
                hasAi && ai.totalScore() != null ? ai.totalScore().intValue() : 0,
                hasAi && ai.hhi() != null ? String.format("%,.0f", ai.hhi()) : "N/A",
                hasAi && ai.winRate() != null ? ai.winRate().doubleValue() : 0.0,
                hasAi ? ai.insightMessage() : "데이터 분석이 필요함",
                holdingsSummary.isEmpty() ? "보유 종목 없음" : holdingsSummary
        );

        return callGemini(prompt,flashModel);
    }

    private static final String AI_ERROR_MESSAGE = AiProvider.ERROR_MESSAGE;

    /** 종목 뉴스 요약: 사실 요약 + 좋게 볼 점 / 조심할 점. 매수·매도 권유와 기사에 없는 숫자는 금지 */
    @Override
    public String getNewsSummary(String code, List<NewsDto> news) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < news.size(); i++) {
            NewsDto n = news.get(i);
            String summary = n.summary() == null || n.summary().equals(n.headline()) ? "" : n.summary();
            if (summary.length() > 400) summary = summary.substring(0, 400) + "…";
            sb.append('[').append(i + 1).append("] ")
              .append(n.dateTime() != null ? n.dateTime().toLocalDate() + " " : "")
              .append(n.headline() == null ? "" : n.headline());
            if (!summary.isBlank()) sb.append("\n    ").append(summary);
            sb.append('\n');
        }
        String prompt = String.format("""
                아래는 미국 주식 %s 관련 최근 영어 뉴스 제목과 요약이야. 한국어로 짧게 정리해.

                [뉴스]
                %s
                [형식] 아래 세 제목을 그대로 쓰고, 각 줄은 '· '로 시작하는 한 문장, 문장 끝에 근거 기사 번호를 [1] 또는 [2,3]처럼 붙여
                핵심 소식
                · (기사들의 중심 내용 2~3줄. 같은 사건은 한 줄로 합치기)
                좋게 볼 수 있는 점
                · (1~2줄. 없으면 '· 뚜렷하지 않아요' 한 줄)
                조심할 점
                · (1~2줄. 없으면 '· 뚜렷하지 않아요' 한 줄)

                [규칙]
                - 기사에 있는 사실만. 기사에 없는 숫자·가격·전망을 만들지 말 것
                - 이 종목을 사라·팔라·보유하라는 말 금지. 주가가 오를지 내릴지 예측 금지
                - 광고성·관련 없는 기사는 무시
                - 마크다운 기호(**, #) 금지, 인사말·서론 금지
                - 쉬운 한국어 존댓말(~예요, ~했어요). 회사 이름과 제품명은 영어 그대로 써도 됨
                """, code, sb);
        return callGeminiStrict(prompt, flashModel, false);
    }
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 실패하면 오류 문구를 돌려준다 */
    private String callGemini(String prompt, String modelPath) {
        try {
            return callGeminiStrict(prompt, modelPath, false);
        } catch (AiException e) {
            return AI_ERROR_MESSAGE;
        }
    }

    /** 실패를 종류별 AiException으로 던진다 */
    private String callGeminiStrict(String prompt, String modelPath, boolean json) {
        // API 키는 URL 쿼리가 아니라 헤더로 보낸다
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);

        GeminiRequest requestDto = new GeminiRequest(
                List.of(new GeminiRequest.Content(
                        List.of(new GeminiRequest.Part(prompt))
                ))
        );
        if (json) requestDto.setGenerationConfig(java.util.Map.of("responseMimeType", "application/json"));

        String responseJson;
        try {
            HttpEntity<GeminiRequest> entity = new HttpEntity<>(requestDto, headers);
            responseJson = restTemplate.postForObject(baseUrl + modelPath, entity, String.class);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            String body = e.getResponseBodyAsString();
            if (e.getStatusCode().value() == 429 || body.contains("RESOURCE_EXHAUSTED")) {
                boolean daily = body.contains("PerDay") || body.contains("per day") || body.contains("billing") || body.contains("check your plan");
                log.warn("[AI] 사용량 소진 ({}): {}", daily ? "하루/결제 한도" : "분당 한도", abbreviate(body));
                throw new AiException(daily ? AiException.Kind.QUOTA_DAILY : AiException.Kind.RATE_LIMIT, "quota");
            }
            log.error("[AI 분석 에러] HTTP {}: {}", e.getStatusCode().value(), abbreviate(body));
            throw new AiException(AiException.Kind.ERROR, "http " + e.getStatusCode().value());
        } catch (Exception e) {
            log.error("[AI 분석 에러] {}: {}", e.getClass().getSimpleName(), e.getMessage());
            throw new AiException(AiException.Kind.ERROR, e.getClass().getSimpleName());
        }
        if (responseJson == null) {
            log.warn("[AI 분석] 빈 응답");
            throw new AiException(AiException.Kind.ERROR, "empty");
        }
        try {
            JsonNode root = MAPPER.readTree(responseJson);
            JsonNode candidate = root.path("candidates").path(0);
            JsonNode text = candidate.path("content").path("parts").path(0).path("text");
            JsonNode usage = root.path("usageMetadata");
            if (!usage.isMissingNode()) {
                log.info("[AI] 토큰 사용 입력 {} · 출력 {} · 합계 {}", usage.path("promptTokenCount").asInt(),
                        usage.path("candidatesTokenCount").asInt(), usage.path("totalTokenCount").asInt());
            }
            // 안전 필터 차단·토큰 초과 등으로 본문이 없을 수 있다
            if (text.isMissingNode() || text.asText().isBlank()) {
                log.warn("[AI 분석] 응답에 본문 없음 finishReason={} blockReason={}",
                        candidate.path("finishReason").asText("-"),
                        root.path("promptFeedback").path("blockReason").asText("-"));
                throw new AiException(AiException.Kind.BLOCKED, "no text");
            }
            return text.asText();
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AI 분석 에러] 응답 해석 실패: {}", e.getMessage());
            throw new AiException(AiException.Kind.ERROR, "parse");
        }
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        String one = s.replaceAll("\\s+", " ");
        return one.length() > 300 ? one.substring(0, 300) + "…" : one;
    }

    /** 종목 AI 리포트: 앱 데이터 + 맥락을 묶어 */
    @Override
    public String getStockReport(StockAnalysisResult analysis, String context) {
        String code = analysis.getCode();
        TradingSignalResult signal = analysis.getTradingSignalResult();
        boolean hasSignal = signal != null && signal.analysis() != null
                && signal.analysis().phase() != ChartPhase.INSUFFICIENT_DATA;
        TradingSignalResult.SignalStrength strength = (hasSignal && signal.strength() != null)
                ? signal.strength() : TradingSignalResult.SignalStrength.empty();

        String prompt = String.format("""
                너는 개인 투자자를 돕는 차분한 주식 코치야. 아래 앱 데이터만 보고 '%s' 종목 리포트를 JSON으로 써.
                통화: %s

                [앱 데이터]
                종합 점수 %d/100 (%s등급) · 가치 %d/40 (%s) · 수급 %d/30 (%s) · 차트 %d/30 (%s)
                차트 국면: %s
                타점: %s
                위험 크기: %s
                신호: 매수 쪽 %d%%(%s) · 매도 쪽 %d%%(%s) · 지켜볼 것(%s)
                엔진 판정: %s

                [추가 맥락]
                %s

                [알아 둘 사실]
                - 이 앱의 과거 시험에서 차트 타점(눌림·돌파 등)은 아무 날에 산 것보다 나은 점이 확인되지 않았어. 근거가 있던 건 미국 12-1개월 모멘텀 상위 20%%를 분기마다 바꾸는 규칙뿐이야
                - 그래서 차트 판정은 '산다면 어떻게, 틀리면 어디서 정리'를 정하는 용도로만 써

                [출력: JSON 하나만]
                {
                  "headline": "지금 이 종목을 한 문장으로 (35자 이내)",
                  "tags": ["상태 키워드 2~3개, 각 8자 이내 (예: 눌림 대기, 모멘텀 상위, 실적 D-5)"],
                  "situation": ["지금 상황 2~3문장. 점수 사이의 엇갈림, 차트 국면, 모멘텀·시장을 엮어서"],
                  "up": {"when": "오르는 쪽 조건 (데이터의 가격 사용)", "then": "그때 할 일"},
                  "down": {"when": "내리는 쪽 조건 (손절가 등)", "then": "그때 할 일"},
                  "actions": ["지금 할 일 2~3개, 순서대로, 각 한 문장"],
                  "risks": ["조심할 점 1~2개"]
                }

                [규칙]
                - tags · situation · actions · risks는 문장이 하나여도 반드시 배열 ["…"]로 쓰기
                - 엔진 판정과 어긋나는 말 금지 (대기·추격 위험·손익비 부족·계획 없음인데 지금 사라고 하기 금지)
                - 사라·팔라고 권하지 말고 '산다면', '가지고 있다면'처럼 조건으로 말하기. 오른다고 장담 금지
                - 데이터에 없는 가격·숫자 만들기 금지. N/A나 없음인 가격은 조건으로만 말하기
                - 가격은 위 통화 형식으로만 쓰기. 신호 문구 속 통화 없는 숫자(PP: 408 등)도 같은 통화야
                - 실적 발표가 7일 안이면 risks에 갭 위험을 넣기
                - 점수 숫자를 늘어놓지 말기 (가격은 써도 됨)
                - 쉬운 한국어 존댓말(~예요, ~해요), 각 문장 60자 이내, 마크다운 기호 금지
                """,
                code,
                isKrCode(code) ? "원화. 가격은 '12,300원'처럼 쓰기" : "미국 달러. 가격은 반드시 '$408.50'처럼 $를 앞에 붙이고, '원'은 절대 쓰지 않기",
                analysis.getScore(), analysis.getGrade(),
                analysis.getValuation().getScore(), joinOrNone(analysis.getValuation().getSignals()),
                analysis.getMomentum().getScore(), joinOrNone(analysis.getMomentum().getSignals()),
                analysis.getTechnical().getScore(), joinOrNone(analysis.getTechnical().getSignals()),
                hasSignal ? describePhase(signal) : "차트 데이터 부족으로 판정 불가",
                hasSignal ? describePlan(code, signal) : "산출 불가",
                hasSignal ? describeRisk(signal) : "산출 불가",
                strength.buyStrength(), joinOrNone(strength.buySignals()),
                strength.sellStrength(), joinOrNone(strength.sellSignals()),
                joinOrNone(strength.watchSignals()),
                hasSignal ? verdictLabel(signal.verdict()) : "데이터 부족",
                context == null || context.isBlank() ? "없음" : context
        );
        return callGeminiStrict(prompt, flashModel, true);
    }

    @Override
    public String getNewsSectorImpact(String news, String sectors) {
        String prompt = String.format("""
                너는 뉴스가 어떤 업종에 영향을 줄지 짚어 주는 애널리스트야. 아래 오늘 뉴스 제목만 보고 JSON으로 써.

                [오늘 뉴스 제목]
                %s

                [쓸 수 있는 섹터 key — 이 목록 밖의 key 금지]
                %s

                [출력: JSON 하나만]
                {
                  "items": [
                    {"title": "뉴스 내용을 한국어 한 문장으로 (40자 이내)",
                     "sectors": [{"key": "위 목록의 key", "dir": 1 또는 -1, "why": "왜 그 방향인지 한 문장 (40자 이내)"}]}
                  ]
                }

                [규칙]
                - 업종에 뚜렷한 영향이 있을 만한 뉴스만 최대 6개. 개별 기업 소식·영향이 애매한 뉴스는 빼기
                - 뉴스 하나에 섹터 1~4개. dir 1 = 그 섹터에 좋은 소식, -1 = 나쁜 소식
                - 같은 주제 뉴스가 여러 개면 하나로 합치기
                - 사라·팔라·지금 타라 같은 매매 권유 금지. 오른다·내린다 장담 금지 (영향 방향만)
                - 뉴스에 없는 사실·숫자 만들기 금지
                - 쉬운 한국어 존댓말, 마크다운 기호 금지
                """, news, sectors);
        return callGeminiStrict(prompt, flashModel, true);
    }

    @Override
    public String getPortfolioReview(String facts, String context, String previous) {
        String prompt = String.format("""
                너는 개인 투자자의 포트폴리오를 매주 같이 점검하는 차분한 코치야. 아래 앱 데이터만 보고 이번 주 점검을 JSON으로 써.

                [이번 주 앱 데이터 — 판정은 앱 규칙이 이미 냈어]
                %s

                [추가 맥락]
                %s

                [지난 점검 때 데이터]
                %s

                [알아 둘 사실]
                - 이 앱에서 시험으로 근거가 확인된 건 미국 12-1개월 모멘텀 상위 20%%를 분기마다 바꾸는 규칙뿐이야
                - 물타기·불타기·분할매수는 시험에서 한 번에 사서 들고 가는 것보다 나아지지 않았어
                - 기준선(손절선·수익 보호선)을 깨면 정리하는 게 앱의 원칙이야. 기준선을 내리지 않아

                [출력: JSON 하나만]
                {
                  "headline": "이번 주 포트폴리오를 한 문장으로 (35자 이내)",
                  "tags": ["상태 키워드 2~3개, 각 8자 이내 (예: 정리 1개, 비중 초과, 실적 주간)"],
                  "changes": ["지난 점검 대비 바뀐 점 1~3개. 지난 데이터가 없으면 빈 배열"],
                  "todo": ["이번 주 할 일 1~4개, 순서대로. 앱 '지금 할 일'과 판정에서만 가져오기"],
                  "watch": [{"name": "종목 이름", "why": "왜 지켜볼지 한 문장"}],
                  "schedule": ["다음 2주 일정 0~3개 (실적 발표 · 분기 교체일). 맥락에 있는 날짜만"]
                }

                [규칙]
                - 모든 칸은 문장이 하나여도 반드시 배열로 쓰기. watch는 최대 3개, 매도·주의 판정 종목 먼저
                - 앱 판정과 어긋나는 말 금지. 유지 종목을 팔라고, 매도 판정 종목을 더 들고 가라고 하지 말기
                - 새로 사라·더 사라(물타기·불타기 포함) 권하지 말기. 오른다·내린다 장담 금지
                - 데이터에 없는 숫자·날짜·금액 만들기 금지. 금액은 쓰지 말고 비율(%%)만
                - 할 일이 없으면 todo에 '기준선만 지키기' 하나만
                - 쉬운 한국어 존댓말(~예요, ~해요), 각 문장 60자 이내, 마크다운 기호 금지
                """,
                facts,
                context == null || context.isBlank() ? "없음" : context,
                previous == null || previous.isBlank() ? "없음 (첫 점검)" : previous
        );
        return callGeminiStrict(prompt, flashModel, true);
    }

    @Override
    public String getMarketAnalysis(EngineInput input, List<NewsDto> newsList, MarketDiagnosisHistoryDto historyDto, String rules) {
        // 퀀트 로우 데이터 JSON 변환
        String rawDataJson = "{}";
        try {
            if (input != null) {
                rawDataJson = new ObjectMapper().writeValueAsString(input);
            }
        } catch (Exception e) {
            rawDataJson = "데이터 직렬화 실패";
        }

        // 실시간 핵심 뉴스 데이터 추출
        String newsData = "최신 뉴스 데이터 없음";
        if (newsList != null && !newsList.isEmpty()) {
            newsData = newsList.stream()
                    .filter(n -> n.headline() != null && !n.headline().isBlank())
                    .limit(15)
                    .map(n -> String.format("- [%s|%s] %s",
                            n.category() != null ? n.category() : "일반",
                            n.source() != null ? n.source() : "언론사",
                            n.headline().trim()))
                    .collect(Collectors.joining("\n"));
        }

        // 직전 진단 기록 (변화 추적용)
        String prevDiagnosisText = buildPrevDiagnosisText(historyDto);

        String prompt = String.format("""
당신은 글로벌 금융 통신사에서 증시 브리핑을 작성하는 시장 담당 애널리스트임
12개 퀀트 분석 엔진의 로우 데이터(JSON)와 실시간 뉴스(내러티브), 그리고 직전 진단 기록을 함께 분석하여 현재 시장의 상태, 주요 동인, 위험 요인, 직전 대비 변화를 하나의 일관된 시장 브리핑으로 작성할 것

중요한 원칙은 퀀트 데이터와 뉴스가 같은 현상을 가리키는지 교차검증하는 것임
뉴스가 퀀트 지표의 원인이라고 단정하지 말고, 실제 데이터로 확인되는 사실과 언론에서 제기하는 해석을 구분할 것

[1. 퀀트 로우 데이터 (12개 분석기 세부 수치)]
%s

[2. 실시간 핵심 뉴스 헤드라인 (내러티브 및 촉매 파악용 - 최신 15건)]
%s

[3. 직전 진단 기록 (연속성 및 변화 파악용)]
%s

[4. 앱 규칙과 이번 주 사실 ('오늘 어떻게 볼지'와 '대응'은 이 안에서만)]
%s

[🚨 절대 엄수 지시사항]

1. 퀀트와 뉴스의 교차검증

뉴스와 퀀트 데이터를 다음 순서로 분석할 것

① QUANT
퀀트 데이터에서 실제로 확인되는 시장 상태를 먼저 파악할 것

② NEWS
최근 뉴스에서 반복적으로 등장하는 핵심 테마와 시장 해석을 파악할 것

③ CROSS VALIDATION
뉴스의 주장과 퀀트 데이터가 일치하는지 확인할 것

다음 세 가지 상황을 구분할 것

- 뉴스와 퀀트가 동일한 현상을 보여주는 경우
  → "뉴스에서 제기된 우려와 퀀트 데이터가 같은 방향을 가리키고 있습니다"와 같이 표현할 것

- 뉴스에서는 주장하지만 퀀트 데이터에서 확인되지 않는 경우
  → "뉴스에서는 해당 우려가 제기되고 있지만, 현재 퀀트 데이터에서는 뚜렷하게 확인되지 않습니다"와 같이 표현할 것

- 퀀트 데이터에서 나타나지만 뉴스에서는 크게 다뤄지지 않는 경우
  → "뉴스보다 퀀트 데이터에서 먼저 나타난 변화입니다"와 같이 표현할 것

뉴스가 특정 시장 움직임의 원인이라고 명시적으로 보도하지 않는 한
"뉴스 A 때문에 지표 B가 움직였다"와 같은 직접적인 인과관계를 임의로 만들어내지 말 것

대신
"뉴스에서 A가 부각되는 가운데, 퀀트 데이터에서는 B가 확인됩니다"
"뉴스의 A 우려는 퀀트 데이터의 B 현상과 부합합니다"
처럼 사실과 해석의 관계를 표현할 것

역사적 비교나 과거 사례가 뉴스에 등장할 경우
그 내용을 사실로 확정하지 말고 반드시 언론의 비교 또는 주장으로 표현할 것

예:
"CNBC는 현재의 시장 폭 축소 현상을 1999년 이후의 시장 상황과 비교하고 있습니다"
처럼 표현할 것

"현재 시장이 1999년 IT 버블과 유사합니다"
"현재 시장은 버블 상태입니다"
처럼 역사적 비교를 독립적인 사실로 확정하지 말 것


2. 수치 인용은 절제해서 사용할 것

- 각 소제목마다 숫자는 최대 1~2개까지만 사용할 것
- 전체 4개 소제목을 통틀어 숫자는 최대 5~6개까지만 사용할 것
- 한 문장에는 숫자를 최대 1개까지만 사용할 것
- 여러 지표의 수치를 나열하지 말 것
- 가장 중요한 근거가 되는 지표 하나만 숫자로 제시할 것
- 나머지 관련 지표는 숫자 없이 방향성으로 표현할 것
- 직전 진단의 수치를 인용하는 경우에도 위 숫자 개수 제한에 포함할 것

예:
"S&P500이 100점을 기록하고 나스닥이 70점이며 신용은 80점으로..." 금지

대신:
"시장 추세 점수는 100점으로 강한 상승 추세를 나타냈습니다."
"나스닥과 신용시장도 대체로 같은 방향을 보이고 있습니다."

처럼 핵심 숫자 하나만 사용하고 나머지는 방향성으로 설명할 것


3. 단순 뉴스 나열 금지

개별 뉴스 기사를 하나씩 요약하지 말 것

최근 뉴스 전체에서 반복되는 공통 테마를 추출할 것

예:
- AI 성장 기대
- 금리 변화
- 지정학적 리스크
- 원자재 가격
- 기업 실적
- 방어주에서 성장주로의 자금 이동
등과 같이 시장 전체에 영향을 미치는 상위 내러티브로 통합할 것

특정 뉴스의 주장이 중요하다면 그 주장을 전체 시장 데이터와 비교하여 설명할 것

뉴스에 실적 발표, 경제지표 발표, 연준 인사 발언 등 앞으로 예정된 이벤트가 등장한다면
"시장은 ~를 앞두고 있습니다", "~ 발표를 앞두고 관망 흐름이 나타나고 있습니다"처럼 언급할 수 있음
단, 뉴스에 등장하지 않은 일정이나 기업명은 절대 만들어내지 말 것


4. 스마트 머니 표현 주의

실제 자금 흐름 데이터가 존재하지 않는 경우
"스마트 머니가 유입됐습니다"
"기관이 매수했습니다"
"큰손이 이동했습니다"
와 같이 확인되지 않은 자금 흐름을 사실처럼 표현하지 말 것

대신 실제 상대강도와 섹터 데이터를 기반으로
"상승이 특정 섹터에 집중되고 있습니다"
"성장 관련 섹터의 상대강도가 두드러집니다"
"방어적 섹터는 상대적인 약세를 보였습니다"
등으로 표현할 것


5. 인과관계 과장 금지

퀀트 데이터는 시장에서 발생한 현상을 보여주는 것이며
그 현상의 원인을 항상 증명하는 것은 아님

따라서 다음 표현을 우선 사용할 것

- "~한 가운데 ~가 확인됩니다"
- "~와 ~가 같은 방향을 나타내고 있습니다"
- "~와 부합하는 흐름입니다"
- "~에 대한 우려가 제기되는 가운데 ~가 나타났습니다"
- "~는 아직 퀀트 데이터에서 뚜렷하게 확인되지 않습니다"

다음과 같은 단정은 피할 것

- "~ 때문에 반드시 상승합니다"
- "~ 때문에 하락했습니다"
- "~가 원인입니다"
- "~를 증명합니다"

단, 뉴스 기사에서 원인이 명시적으로 보도된 경우에는
"언론에서는 ~를 원인으로 꼽고 있습니다"처럼 출처의 해석으로 귀속할 것


6. 시장 위험과 시장 강도를 동시에 표현할 것

시장 지수가 강하더라도 시장 폭이 약하거나 특정 섹터에 상승이 집중되어 있다면
이를 별도의 위험 신호로 설명할 것

반대로 시장 폭이 약하더라도 신용, 유동성, 위험선호 등이 안정적인 경우
시장 전체의 시스템 스트레스로 과장하지 말 것

특정 경고 신호 하나만으로
"시장 붕괴"
"버블"
"대폭락"
등의 극단적인 결론을 내리지 말 것


7. 포지셔닝 전략 표현

현재 시장 국면에 대한 포트폴리오 관점의 해석은 가능하지만
특정 종목의 매수·매도나 개인 투자자의 구체적인 투자 행동을 지시하지 말 것

"공격적인 성장주 비중 확대"
"선별적 롱 포지션"
"특정 자산 매수"
같은 직접적인 투자 지시 대신

- 성장주에 우호적인 환경
- 위험자산 선호가 이어지는 환경
- 방어적 접근이 필요한 환경
- 시장 전체보다 주도 섹터의 상대강도를 살펴볼 필요
- 시장 폭 개선 여부를 확인할 필요
- 신용시장 악화 여부를 지켜볼 필요

처럼 현재 국면에 대한 포트폴리오 관점의 해석과 관찰 포인트를 제시할 것

반드시 퀀트 데이터와 뉴스의 교차검증 결과를 근거로 판단할 것

예외: "stance"와 "actions" 칸은 [4. 앱 규칙과 이번 주 사실]에 있는 규칙을 오늘 상황에 맞게 옮겨 말하는 칸임
- stance: 오늘 시장을 어떻게 바라보면 좋을지 한두 문장. 퀀트·뉴스 결론과 [4]의 시장 40주선 상태를 함께 엮을 것
- actions: [4]의 규칙에서 나온 행동만 2~3개 (새로 살 때 크기 · 보유 종목은 기준선대로 · 모멘텀 교체일 · 이번 주 발표 전후 주의)
- 개별 종목·섹터·자산을 사라/팔라, 비중을 늘려라/줄여라는 지시는 여전히 금지. [4]에 없는 규칙을 만들지 말 것
- 시장 상태만 보고 보유 종목을 미리 팔라고 하지 말 것 (앱의 시험 결과: 시장 규칙으로 보유를 파는 건 지수 보유를 이기지 못했음)
- 오른다·내린다 예측 금지. 발표 일정은 [4]에 있는 것만


8. 직전 진단 활용 원칙

[3. 직전 진단 기록]은 현재 진단이 직전과 비교해 이어지는지, 바뀌는지를 설명하는 용도로만 사용할 것

판단의 근거는 항상 [1. 퀀트 로우 데이터]의 현재 수치이며
직전 진단의 해석 문장을 현재 판단의 근거로 재사용하지 말 것

직전 진단의 매크로 해석 문장은 과거에 생성된 해석일 뿐 확정된 사실이 아니므로
그대로 복사하거나 반복하지 말 것
현재 데이터와 달라졌다면 달라진 점을 중심으로 새로 서술할 것
직전 진단의 문체가 이번 지시와 다르더라도 그 문체를 따라 하지 말 것

다음 세 가지 상황을 구분할 것

- 시장 국면이 직전과 동일한 경우
  → 국면이 며칠째 이어지고 있는지 연속성을 간결하게 언급하고, 그 안에서 달라진 세부 신호가 있다면 함께 짚을 것
  → 변화가 없는데 변화가 있는 것처럼 과장하지 말 것

- 시장 국면이 직전과 달라진 경우
  → 어떤 국면에서 어떤 국면으로 전환되었는지 명시하고, 전환의 근거가 되는 현재 퀀트 신호 하나를 제시할 것
  → 한 번의 전환만으로 추세 반전을 단정하지 말고 확인 과정이 필요함을 함께 표현할 것

- 세부 점수만 개선 또는 악화된 경우
  → 국면 변화가 아닌 내부 강도의 변화로 구분하여 표현할 것
  → "직전 진단보다 ~가 개선됐습니다", "직전 진단보다 ~가 약해졌습니다"처럼 방향성 중심으로 표현할 것

직전 진단 기록이 없거나 "기록 없음"으로 표시된 경우
직전 대비 변화에 대해 언급하지 말고 현재 상태 진단만 작성할 것
기록이 없는 사실 자체를 언급하지도 말 것

직전 진단의 날짜와 현재 시점 사이에 간격이 있을 수 있으므로
"어제", "전일" 같은 표현을 쓰지 말고 "직전 진단"이라고 표현할 것

직전 대비 변화는 "change" 칸에 한 문장으로만 쓸 것


9. 문체

- 쉬운 한국어 존댓말 해요체로 쓸 것 ("~예요", "~했어요", "~있어요", "~보여요")
- 합니다체("~습니다")와 명사형 종결("~함", "~임", "~필요")은 쓰지 말 것
- 문장의 주어를 구체적으로 쓸 것 ("시장은" 대신 "시장 추세 점수는", "반도체 섹터는", "신용 지표는")
- 사실을 먼저 쓰고 해석은 그 뒤에 붙일 것
- 한 문장에는 핵심 주장 하나만, 60자 안팎으로 쓸 것
- 과장되거나 수사적인 표현을 쓰지 말 것 (예: "본질적인", "폭풍전야", "강력한 경고", "주목할 만한", "도사리고 있는")
- 번역투를 피할 것 (예: "이러한 발전은", "~에 의해 영향을 받았습니다", "~하는 것으로 보여집니다")
- 마크다운 기호와 이모지는 쓰지 말 것
- 직접적인 매수·매도 지시나 특정 자산의 비중 조절 지시는 하지 말 것

문체 예시 (내용은 참고하지 말고 말투만 참고할 것)
"시장 추세 점수는 85점으로 상승 추세를 이어가고 있어요."
"다만 상승 종목 비율은 직전 진단보다 줄어, 상승이 일부 대형 기술주에 몰려 있어요."


10. 출력: JSON 하나만 (설명·코드 울타리 없이)

{
  "headline": "지금 시장을 한 문장으로 (35자 이내)",
  "tags": ["시장 상태 키워드 2~3개, 각 8자 이내 (예: 상승 추세, 시장 폭 축소, 금리 부담)"],
  "stance": "오늘 시장을 어떻게 바라볼지 한두 문장 (90자 이내). 예: 추세는 살아 있지만 상승이 일부 업종에 몰려 있어요. 새로 살 때는 평소 크기를 지키고, 보유는 기준선대로 보면 되는 날이에요",
  "actions": ["오늘의 대응 2~3개, 각 한 문장. [4]의 규칙에서만 (예: 미국 종목은 평소 크기로, 한국 종목은 절반 크기로 새로 사요 / 보유 종목은 시장 분위기보다 각자 기준선을 봐요 / 목요일 CPI 발표 전후로 갭이 날 수 있어요)"],
  "change": "직전 진단과 비교한 변화 한 문장. 직전 기록이 없으면 빈 문자열",
  "regime": ["매크로 및 시장 국면 진단 2~3문장. 추세·모멘텀·금리·유동성·매크로를 종합하고, 가장 결정적인 지표 하나만 숫자로. 뉴스의 거시 이슈가 데이터와 맞는지 교차검증"],
  "drivers": ["주도 섹터와 뉴스 테마 2~3문장. 섹터 데이터가 보여주는 상대강도와 뉴스가 제기하는 촉매를 구분. 확인되지 않은 자금 흐름을 만들어내지 말 것"],
  "risks": ["위험 신호 2~3문장. 실제로 확인되는 위험과 가능성 단계의 위험을 구분하고, 가장 중요한 위험 지표 하나만 숫자로. 단일 지표로 버블·붕괴를 확정하지 말 것"],
  "watch": ["앞으로 지켜볼 것 2~3개, 각 한 문장. 시장 강도와 내부 취약성을 함께 보고, 뉴스에 예정된 이벤트가 있으면 포함"]
}

- tags · actions · regime · drivers · risks · watch는 문장이 하나여도 반드시 배열로 쓸 것
- 숫자는 전체를 통틀어 5~6개까지만 쓸 것
""",
                rawDataJson,
                newsData,
                prevDiagnosisText,
                rules == null || rules.isBlank() ? "없음 (stance와 actions는 퀀트 결론만으로 짧게, 보유 종목은 기준선대로라고만)" : rules
        );

        try {
            return callGeminiStrict(prompt, flashModel, true).strip();
        } catch (AiException e) {
            // 사용량 소진은 화면에서 따로 안내한다
            return e.isQuota() ? AiProvider.QUOTA_MESSAGE : AI_ERROR_MESSAGE;
        }
    }

    private static String buildPrevDiagnosisText(MarketDiagnosisHistoryDto h) {
        if (h == null) {
            return "기록 없음";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("- 진단 기준일: ").append(h.date() != null ? h.date() : "미상").append('\n');
        sb.append("- 시장 국면: ").append(h.regime()).append(" (")
                .append(h.regimeStreakDays()).append("일째 지속)").append('\n');
        sb.append("- 위험 선호: ").append(h.risk()).append('\n');
        sb.append("- 종합 컨디션 점수: ").append(String.format("%.1f", h.compositeScore()))
                .append(" (범위 -100~100, 분석 신뢰도 ").append(h.confidence()).append("/100)").append('\n');
        sb.append("- 분석기별 점수(-100~100): ")
                .append("추세 ").append(Math.round(h.trendScore()))
                .append(", 모멘텀 ").append(Math.round(h.momentumScore()))
                .append(", 시장폭 ").append(Math.round(h.breadthScore()))
                .append(", 금리 ").append(Math.round(h.ratesScore()))
                .append(", 유동성 ").append(Math.round(h.liquidityScore()))
                .append(", 매크로 ").append(Math.round(h.macroScore()))
                .append(", 신용 ").append(Math.round(h.creditScore()))
                .append(", 섹터 ").append(Math.round(h.sectorScore()))
                .append(", 방어 ").append(Math.round(h.defensiveScore()))
                .append(", 크로스에셋 ").append(Math.round(h.crossAssetScore())).append('\n');
        sb.append("- 거시 국면: ").append(h.macroRegime() != null ? h.macroRegime() : "미상").append('\n');
        sb.append("- 시장 폭 다이버전스 경고: ").append(h.hasBreadthDivergence() ? "있음" : "없음").append('\n');

        String narrative = h.macroNarrative();
        if (narrative != null && !narrative.isBlank()) {
            String trimmed = narrative.trim();
            if (trimmed.length() > 400) {
                trimmed = trimmed.substring(0, 400) + "…";
            }
            sb.append("- 직전 매크로 해석(과거 생성 문장, 사실 아님): ").append(trimmed).append('\n');
        }
        return sb.toString().trim();
    }
}