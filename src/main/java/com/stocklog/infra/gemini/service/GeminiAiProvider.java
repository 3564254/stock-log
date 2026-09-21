package com.stocklog.infra.gemini.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocklog.infra.gemini.dto.GeminiRequest;
import com.stocklog.modules.ai.AiProvider;
import com.stocklog.modules.market.analysis.dto.EngineInput;
import com.stocklog.modules.market.news.dto.NewsDto;
import com.stocklog.modules.purchase.portfolio.analysis.dto.PortfolioAnalysisResult;
import com.stocklog.modules.purchase.portfolio.dto.response.PortfolioAnalysisResponse;
import com.stocklog.modules.stock.analysis.domain.signal.vo.EntrySignal;
import com.stocklog.modules.stock.analysis.result.StockAnalysisResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GeminiAiProvider implements AiProvider {
    // TODO(패턴): AiProvider를 구현한 이 클래스는 외부 SDK/HTTP를 숨기는 Adapter로서 적절하다.
    // 다만 모든 예외를 일반 문자열로 바꾸면 호출자가 실패와 정상 분석을 구분할 수 없다.
    // 도메인 예외 또는 Result 타입으로 변환하고, timeout·재시도·fallback 정책은 이 Adapter 경계에 모아 보자.

    @Value("${gemini.api.url}")
    private String apiUrl;

    @Value("${gemini.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate;

    @Override
    public String getStockAnalysis(StockAnalysisResult analysis) {
        // ✨ 주의: StockAnalysisResult 엔티티/DTO 내부의 타입도 TradePositionResult로 변경되어 있어야 함
        EntrySignal tpr = analysis.getEntrySignal();
        boolean hasTpr = (tpr != null);

        String prompt = String.format("""
                당신은 여의도 탑티어 증권사의 프랍 트레이딩(Prop Trading) 퀀트 수석 애널리스트야. 당신의 임무는 주어진 '%s' 종목의 데이터를 분석해, 투자자가 당장 '어떤 행동(Action)'을 취해야 하는지 명확하고 구체적인 지침을 내리는 거야.

                [분석 데이터]
                종합: %d/100 (%s)
                가치: %d/40 (%s) | 수급: %d/30 (%s) | 차트: %d/30 (%s)
                타점: 1차 매수가 %s / 목표가 %s / 손절가 %s
                신호: 매수강도 %d%%(%s), 매도강도 %d%%(%s)
                AI판단: %s

                [🚨 절대 엄수 지시사항 - 위반 시 해고]
                1. 행동 중심(Action-Oriented) 서술: "그래서 지금 어떻게 해야 하는가?"에 대한 명확한 답을 제시해 (예: 현재가 추격 매수 금지, X원까지 눌림목 대기 후 3분할 진입, 보유자 영역이므로 신규 진입 보류 등)
                2. 앵무새 금지: 가치 X점, 수급 Y점 같은 단순 수치 나열 절대 금지 (숫자는 제시된 타점 가격만 노출할 것)
                3. 데이터의 이면 해석: 점수의 괴리(예: 가치↑ 차트↓)를 통해 세력의 의도나 시장 심리를 읽어내어 '행동 지침의 근거'로만 사용해
                4. 문체 및 포맷:
                   - 인사말과 서론 절대 생략
                   - 마크다운 볼드체(**) 등 모든 기호 사용 절대 금지
                   - 모든 문장의 끝은 반드시 간결한 명사형으로 종결 (~함, ~임, ~요망, ~필요, ~대기, ~권장 등)
                   - 문장 끝에 마침표(.) 등 문장 부호 절대 금지

                아래 4가지 소제목을 사용하여 밀도 있고 날카롭게 작성해 (각 2~3문장).

                📊 현재 국면 및 시장 심리 (가치/수급/차트 데이터의 충돌을 분석해 현재 주가 위치의 본질을 진단)
                🔮 단기 주가 시나리오 (매수/매도 강도와 AI판단을 융합하여 예상되는 상승/하락 경로 제시)
                ⚔️ 구체적 액션 플랜 및 타점 (제시된 매수가/목표가를 바탕으로 '언제, 어떻게, 얼마나' 사야 하는지 명확한 행동 지침 제안)
                🛡️ 리스크 대응 매뉴얼 (손절가 이탈 시 뇌동매매를 막기 위한 기계적인 하방 방어 행동 수칙 제시)

                ※ 마지막 줄에는 반드시 다음 문구를 그대로 출력할 것: ※ 본 분석은 퀀트 데이터 기반의 참고용이며, 투자 권유 아님
                """,
                analysis.getCode(),
                analysis.getScore(),
                analysis.getGrade(),
                analysis.getValuation().getScore(),
                String.join(", ", analysis.getValuation().getSignals()),
                analysis.getMomentum().getScore(),
                String.join(", ", analysis.getMomentum().getSignals()),
                analysis.getTechnical().getScore(),
                String.join(", ", analysis.getTechnical().getSignals()),
                hasTpr ? String.format("%.0f", tpr.primaryEntry()) : "N/A",
                hasTpr ? String.format("%.0f", tpr.primaryTarget()) : "N/A",
                hasTpr ? String.format("%.0f", tpr.stopLoss()) : "N/A",
                hasTpr ? tpr.buyStrength() : 0,
                hasTpr && !tpr.buySignals().isEmpty() ? String.join(", ", tpr.buySignals()) : "없음",
                hasTpr ? tpr.sellStrength() : 0,
                hasTpr && !tpr.sellSignals().isEmpty() ? String.join(", ", tpr.sellSignals()) : "없음",
                hasTpr ? tpr.verdict() : "데이터 부족"
        );

        return callGemini(prompt);
    }

    @Override
    public String getPortfolioAnalysis(PortfolioAnalysisResult analysisResult) {
        PortfolioAnalysisResponse ai = analysisResult.analysis();
        boolean hasAi = (ai != null);

        String holdingsSummary = analysisResult.myStocks().stream()
                .map(s -> String.format("- %s (%s)", s.korName(), s.sector()))
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
                hasAi ? ai.totalScore().intValue() : 0,
                hasAi ? String.format("%,.0f", ai.hhi()) : "N/A",
                hasAi ? ai.winRate().doubleValue() : 0.0,
                hasAi ? ai.insightMessage() : "데이터 분석이 필요함",
                holdingsSummary.isEmpty() ? "보유 종목 없음" : holdingsSummary
        );

        return callGemini(prompt);
    }

    private String callGemini(String prompt) {
        String requestUrl = apiUrl + "?key=" + apiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        GeminiRequest requestDto = new GeminiRequest(
                List.of(new GeminiRequest.Content(
                        List.of(new GeminiRequest.Part(prompt))
                ))
        );

        try {
            HttpEntity<GeminiRequest> entity = new HttpEntity<>(requestDto, headers);
            String responseJson = restTemplate.postForObject(requestUrl, entity, String.class);

            JsonNode root = new ObjectMapper().readTree(responseJson);
            return root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();
        } catch (Exception e) {
            return "AI 분석 중 오류 발생";
        }
    }
    @Override
    public String getMarketAnalysis(EngineInput input, List<NewsDto> newsList) {
        // 1. 퀀트 로우 데이터 JSON 변환
        String rawDataJson = "{}";
        try {
            if (input != null) {
                rawDataJson = new ObjectMapper().writeValueAsString(input);
            }
        } catch (Exception e) {
            rawDataJson = "데이터 직렬화 실패";
        }

        // 2. 실시간 핵심 뉴스 데이터 추출 (최신 최대 15개 헤드라인 선별)
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

        String prompt = String.format("""
        당신은 글로벌 탑티어 헤지펀드의 수석 매크로 전략가야. 12개 퀀트 분석 엔진의 로우 데이터(JSON)와 실시간 뉴스(내러티브)를 융합 분석하여, 시장의 본질을 꿰뚫고 당장 취해야 할 포지셔닝 전략을 날카롭게 제시해.

        [1. 퀀트 로우 데이터 (12개 분석기 세부 수치)]
        %s

        [2. 실시간 핵심 뉴스 헤드라인 (내러티브 및 촉매 파악용 - 최신 15건)]
        %s

        [🚨 절대 엄수 지시사항 - 위반 시 해고]
        1. 퀀트와 뉴스의 결합(Synthesis): "뉴스의 A 이슈 때문에 퀀트 지표 B가 이렇게 움직이고 있다"는 식으로 숫자와 내러티브의 인과관계를 반드시 연결해서 설명할 것
        2. 수치 인용은 절제해서 사용할 것:
           - 각 소제목(문단)마다 숫자는 최대 1~2개까지만 인용할 것. 그 이상 쓰지 말 것
           - 문단 전체(4개 소제목)를 통틀어 인용하는 숫자는 최대 5~6개를 넘기지 말 것
           - "A가 90.0점, B가 -50.0점, C가 6.39%%로..." 처럼 여러 지표를 나열하는 문장은 절대 쓰지 말 것. 한 문장에는 숫자를 하나만 넣을 것
           - 숫자를 뒷받침 근거로 쓸 지표를 먼저 하나 고르고, 나머지 관련 지표들은 숫자 없이 "~도 함께 약세를 보이며" 같은 방향성 서술로만 표현할 것
        3. 단순 뉴스 나열 금지: 개별 기사를 요약하지 말고, 전체 뉴스를 관통하는 핵심 테마를 추출해 분석할 것
        4. 문체 및 포맷: 
           - 인사말과 서론 절대 생략
           - 마크다운 볼드체 기호 사용 절대 금지
           - 한 문단은 2~3문장을 넘기지 말고, 각 문장은 하나의 주장만 담을 것 (여러 근거를 한 문장에 우겨넣지 말 것)
           - 모든 문장의 끝은 반드시 간결한 명사형으로 종결 (~함, ~임, ~요망, ~필요, ~관망, ~확대, ~축소 등)
           - 문장 끝에 온점 등 문장 부호 절대 금지

        아래 4가지 소제목을 사용하여 밀도 있고 예리하게 작성해 (각 2~3문장, 숫자는 문단당 1~2개 이내).

        🌐 매크로 및 시장 국면 진단 (추세, 모멘텀, 매크로 퀀트 데이터를 종합하여 현재 장세의 본질적 상태 진단 — 가장 결정적인 지표 하나를 중심으로)
        🔥 펀더멘털 및 내러티브 흐름 (주도 섹터 데이터와 핵심 뉴스를 엮어 스마트 머니의 이동 경로와 촉매제 분석)
        ⚠️ 시스템 리스크 및 꼬리 위험 (시장 확산도, 신용 데이터, 악재성 뉴스를 종합하여 잠재적 하방 압력 진단)
        🎯 퀀트-뉴스 융합 포지셔닝 전략 (데이터와 내러티브를 종합하여 현재 국면에 맞는 포트폴리오 비중 조절 액션 플랜 제안)

        ※ 마지막 줄에는 반드시 다음 문구를 그대로 출력할 것: ※ 본 분석은 퀀트 데이터 및 언론 보도 기반의 참고용이며, 투자 권유 아님
        """,
                rawDataJson,
                newsData
        );

        return callGemini(prompt);
    }
}