package com.stocklog.infra.gemini.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocklog.domain.ai.AiApiProvider;
import com.stocklog.domain.portfolio.analysis.dto.PortfolioAnalysisResult;
import com.stocklog.domain.portfolio.dto.response.PortfolioAnalysisResponse;
import com.stocklog.domain.stock.analysis.domain.signal.EntrySignal;
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
        EntrySignal es = analysis.getEntrySignal();
        boolean hasEs = (es != null);

        String prompt = String.format("""
    당신은 탑티어 증권사의 수석 퀀트 애널리스트야. '%s' 종목 데이터를 바탕으로 VIP 고객을 위한 프리미엄 실전 투자 리포트를 작성해.
    
    [데이터]
    종합: %d/100 (%s)
    가치: %d/40 (%s)
    수급: %d/30 (%s)
    차트: %d/30 (%s)
    가격: %s (볼린저하단: %s ~ 상단: %s)
    지표: RSI %.1f / MACD %.4f
    신호: 매수강도 %d%%(%s), 매도강도 %d%%(%s)
    
    [지시사항]
    주어진 데이터를 바탕으로 아래 4가지 소제목을 반드시 사용하여 밀도 있고 전문적으로 작성해. (각 항목당 2~3문장)
    ** 사용 금지.
    자신 설명 금지. 바로 본론으로 들어가기.
    
    📊 종합 판단 및 핵심 근거 (가치/수급/차트 데이터를 바탕으로 한 해당 종목의 강점과 약점 요약)
    🔮 시나리오별 전망 및 목표 밴드 (현재가와 '볼린저 밴드 상/하단 가격'을 활용하여 상승/하락 시나리오와 단기 목표주가 범위 제시)
    ⚔️ 실전 매매 및 진입 전략 (RSI, MACD, 매수 신호 강도를 종합한 구체적인 타점 및 포지션 운영 제안)
    🛡️ 리스크 관리 및 방어 전략 (매도 신호 및 하방 리스크를 대비한 손절 라인과 관망/대응 기준)
    
    ※ 마지막 줄에는 반드시 '※ 본 분석은 데이터 기반의 참고용이며, 투자 권유가 아닙니다.' 라고 명시할 것.
    """,
                analysis.getCode(),
                analysis.getScore(), analysis.getGrade(),
                analysis.getValuation().getScore(), String.join(", ", analysis.getValuation().getSignals()),
                analysis.getMomentum().getScore(), String.join(", ", analysis.getMomentum().getSignals()),
                analysis.getTechnical().getScore(), String.join(", ", analysis.getTechnical().getSignals()),
                hasEs ? String.format("%.0f", es.getCurrentPrice()) : "N/A",
                hasEs ? String.format("%.0f", es.getBollingerLower()) : "N/A",
                hasEs ? String.format("%.0f", es.getBollingerUpper()) : "N/A",
                hasEs ? es.getRsi() : 0.0,
                hasEs ? es.getMacd() : 0.0,
                hasEs ? es.getBuyStrength() : 0,
                hasEs && !es.getBuySignals().isEmpty() ? String.join(", ", es.getBuySignals()) : "없음",
                hasEs ? es.getSellStrength() : 0,
                hasEs && !es.getSellSignals().isEmpty() ? String.join(", ", es.getSellSignals()) : "없음"
        );

        return callGemini(prompt);
    }

    @Override
    public String getPortfolioAnalysis(PortfolioAnalysisResult analysisResult) {
        // PortfolioAnalysisResponse 객체 추출
        PortfolioAnalysisResponse ai = analysisResult.analysis();
        boolean hasAi = (ai != null);

        // 보유 종목 리스트 요약 (종목명과 섹터 정보 추출)
        String holdingsSummary = analysisResult.myStocks().stream()
                .map(s -> String.format("- %s (%s)", s.korName(), s.sector()))
                .collect(java.util.stream.Collectors.joining("\n"));

        // VIP 고객용 프리미엄 리포트 프롬프트 구성
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
** 사용 금지.
자신 설명 금지. 바로 본론으로 들어가기.

📈 포트폴리오 진단 총평 (현재 수익률과 등급을 바탕으로 한 자산 운용 상태의 냉정한 평가)
⚖️ 자산 배분 및 집중도 분석 (HHI 지수와 보유 종목 구성을 분석하여 분산 투자 정도와 위험 요소 지적)
🚀 수익성 개선 전략 (승률과 종목 구성을 바탕으로 기대 수익을 높이기 위한 포지션 조절 제안)
🛡️ 리스크 관리 가이드 (현재 자산 구성에서 가장 취약한 지점과 시장 변동성 대응 방안)

※ 마지막 줄에는 반드시 '※ 본 분석은 데이터 기반의 참고용이며, 투자 권유가 아닙니다.' 라고 명시할 것.
""",
                String.format("%,.0f", analysisResult.totalAssets()), //
                String.format("%,.0f", analysisResult.totalProfit()), //
                analysisResult.totalReturnRate().doubleValue(), //
                hasAi ? ai.grade() : "N/A", //
                hasAi ? ai.totalScore().intValue() : 0, //
                hasAi ? String.format("%,.0f", ai.hhi()) : "N/A", //
                hasAi ? ai.winRate().doubleValue() : 0.0, //
                hasAi ? ai.insightMessage() : "데이터 분석이 필요합니다.", //
                holdingsSummary.isEmpty() ? "보유 종목 없음" : holdingsSummary
        );

        return callGemini(prompt); //
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
            return "AI 분석 중 오류가 발생했습니다.";
        }
    }

}
