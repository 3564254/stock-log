package com.stocklog.infra.gemini.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocklog.domain.ai.AiApiProvider;
import com.stocklog.domain.portfolio.analysis.dto.PortfolioAnalysisResult;
import com.stocklog.domain.portfolio.dto.response.PortfolioAnalysisResponse;
import com.stocklog.domain.stock.analysis.domain.signal.vo.EntrySignal;
import com.stocklog.domain.stock.analysis.result.StockAnalysisResult;
import com.stocklog.infra.gemini.dto.GeminiRequest;
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
public class GeminiService implements AiApiProvider {

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
당신은 여의도 탑티어 증권사의 딥러닝/퀀트 수석 애널리스트야. 당신의 임무는 주어진 '%s' 종목의 한정된 퀀트 데이터를 서로 교차 분석하여, 단순 수치 나열이 아닌 '전문 투자 리포트'를 작성하는 거야.

[분석 데이터]
종합: %d/100 (%s)
가치: %d/40 (%s) | 수급: %d/30 (%s) | 차트: %d/30 (%s)
타점: 1차 매수가 %s / 목표가 %s / 손절가 %s
신호: 매수강도 %d%%(%s), 매도강도 %d%%(%s)
AI판단: %s

[핵심 분석 로직 및 지시사항 (반드시 지킬 것)]
1. 데이터 교차 해석: 
   - '가치'와 '수급/차트'의 괴리를 해석해 (예: 가치는 낮은데 수급이 높으면 '단기 수급 쏠림', 가치는 높은데 차트가 낮으면 '저평가 매집 구간' 등)
   - '타점'과 'AI판단'을 결합해 시장의 심리적 상태와 주도권 향방을 추론해
2. 내용 제한: 기계적인 숫자 나열 금지, 숫자는 논리적 근거로만 사용하고 최종적인 '판단'과 '대응'에 집중해
3. 문체 및 포맷:
   - 인사말 생략 후 바로 본론 시작, ** 기호 사용 금지
   - 문장의 끝은 반드시 명사형으로 종결할 것 (~함, ~임, ~요망, ~필요 등)
   - 문장 끝에 온점(.)을 절대 찍지 말 것

아래 4가지 소제목을 사용하여 밀도 있게 작성해 (각 2~3문장).

📊 종합 분석 및 투자 맥락 (가치와 수급 데이터를 바탕으로 한 현재 종목의 위치 진단)
🔮 주가 경로 및 시나리오 (현재가와 타점 기준 상승/하락 변곡점 제시)
⚔️ 진입 타점 및 운용 가이드 (AI판단을 조합한 구체적인 매매 포지션 제안)
🛡️ 리스크 관리 및 방어선 (하방 리스크를 대비한 손절 라인 및 트레일링 스탑 설정)

※ 마지막 줄에는 반드시 '※ 본 분석은 퀀트 데이터 기반의 참고용이며, 투자 권유 아님' 이라고 명시할 것
""",
                analysis.getCode(),
                analysis.getScore(), analysis.getGrade(),
                analysis.getValuation().getScore(), String.join(", ", analysis.getValuation().getSignals()),
                analysis.getMomentum().getScore(), String.join(", ", analysis.getMomentum().getSignals()),
                analysis.getTechnical().getScore(), String.join(", ", analysis.getTechnical().getSignals()),
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
- 전체 현황: 총 자산 %s원 / 총 수익 %s원 / 수익률 %.2f%%
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
                String.format("%,.0f", analysisResult.totalAssets()),
                String.format("%,.0f", analysisResult.totalProfit()),
                analysisResult.totalReturnRate().doubleValue(),
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
                        List.of(new GeminiRequest.Part(prompt)))));

        try {
            String responseJson = restTemplate.postForObject(
                    requestUrl, new HttpEntity<>(requestDto, headers), String.class);
            JsonNode root = new ObjectMapper().readTree(responseJson);
            return root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();
        } catch (Exception e) {
            return "AI 분석 중 오류 발생";
        }
    }
}