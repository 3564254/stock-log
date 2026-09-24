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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class GeminiAiProvider implements AiProvider {
    // TODO(패턴): AiProvider를 구현한 이 클래스는 외부 SDK/HTTP를 숨기는 Adapter로서 적절하다.
    // 다만 모든 예외를 일반 문자열로 바꾸면 호출자가 실패와 정상 분석을 구분할 수 없다.
    // 도메인 예외 또는 Result 타입으로 변환하고, timeout·재시도·fallback 정책은 이 Adapter 경계에 모아 보자.

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

        return callGemini(prompt,flashModel);
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

        return callGemini(prompt,flashModel);
    }

    private String callGemini(String prompt, String modelPath) {
        String requestUrl = baseUrl + modelPath + "?key=" + apiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // GeminiRequest DTO를 생성하여 Content 및 Part 리스트 구성
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
            log.error("[AI 분석 에러]: {}", e.getMessage());
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
당신은 글로벌 탑티어 헤지펀드의 수석 매크로 전략가임
12개 퀀트 분석 엔진의 로우 데이터(JSON)와 실시간 뉴스(내러티브)를 함께 분석하여 현재 시장의 상태, 주요 동인, 위험 요인을 하나의 일관된 시장 진단으로 통합할 것

중요한 원칙은 퀀트 데이터와 뉴스가 같은 현상을 가리키는지 교차검증하는 것임
뉴스가 퀀트 지표의 원인이라고 단정하지 말고, 실제 데이터로 확인되는 사실과 언론에서 제기하는 해석을 구분할 것

[1. 퀀트 로우 데이터 (12개 분석기 세부 수치)]
%s

[2. 실시간 핵심 뉴스 헤드라인 (내러티브 및 촉매 파악용 - 최신 15건)]
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
  → "뉴스에서 제기된 우려와 퀀트 데이터가 같은 방향을 가리킴"과 같이 표현할 것

- 뉴스에서는 주장하지만 퀀트 데이터에서 확인되지 않는 경우
  → "뉴스에서는 해당 우려가 제기되고 있으나 현재 퀀트 데이터에서는 뚜렷하게 확인되지 않음"과 같이 표현할 것

- 퀀트 데이터에서 나타나지만 뉴스에서는 크게 다뤄지지 않는 경우
  → "뉴스보다 퀀트 데이터에서 먼저 확인되는 변화"와 같이 표현할 것

뉴스가 특정 시장 움직임의 원인이라고 명시적으로 보도하지 않는 한
"뉴스 A 때문에 지표 B가 움직였다"와 같은 직접적인 인과관계를 임의로 만들어내지 말 것

대신
"뉴스에서 A가 부각되는 가운데 퀀트 데이터에서는 B가 확인됨"
"뉴스의 A 우려와 퀀트 데이터의 B 현상이 서로 부합함"
처럼 사실과 해석의 관계를 표현할 것

역사적 비교나 과거 사례가 뉴스에 등장할 경우
그 내용을 사실로 확정하지 말고 반드시 언론의 비교 또는 주장으로 표현할 것

예:
"CNBC는 현재의 시장 폭 축소 현상을 1999년 이후의 특정 시장 상황과 비교하고 있음"
처럼 표현할 것

"현재 시장이 1999년 IT 버블과 유사함"
"현재 시장이 버블 상태임"
처럼 역사적 비교를 독립적인 사실로 확정하지 말 것


2. 수치 인용은 절제해서 사용할 것

- 각 소제목마다 숫자는 최대 1~2개까지만 사용할 것
- 전체 4개 소제목을 통틀어 숫자는 최대 5~6개까지만 사용할 것
- 한 문장에는 숫자를 최대 1개까지만 사용할 것
- 여러 지표의 수치를 나열하지 말 것
- 가장 중요한 근거가 되는 지표 하나만 숫자로 제시할 것
- 나머지 관련 지표는 숫자 없이 방향성으로 표현할 것

예:
"S&P500이 100점을 기록하고 나스닥이 70점이며 신용은 80점으로..." 금지

대신:
"시장 추세 점수가 100점으로 강한 상승 추세가 확인됨"
"나스닥과 신용시장도 전반적으로 같은 방향을 보이는 상황임"

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


4. 스마트 머니 표현 주의

실제 자금 흐름 데이터가 존재하지 않는 경우
"스마트 머니가 유입됨"
"기관이 매수함"
"큰손이 이동함"
과 같이 확인되지 않은 자금 흐름을 사실처럼 표현하지 말 것

대신 실제 상대강도와 섹터 데이터를 기반으로
"상승이 특정 섹터에 집중되는 모습"
"성장 관련 섹터의 상대강도가 두드러짐"
"방어적 섹터의 상대적 약세가 나타남"
등으로 표현할 것


5. 인과관계 과장 금지

퀀트 데이터는 시장에서 발생한 현상을 보여주는 것이며
그 현상의 원인을 항상 증명하는 것은 아님

따라서 다음 표현을 우선 사용할 것

- "~한 가운데 ~가 확인됨"
- "~와 ~가 같은 방향을 나타냄"
- "~와 부합하는 모습임"
- "~에 대한 우려가 제기되는 가운데 ~가 나타남"
- "~는 아직 퀀트 데이터에서 뚜렷하게 확인되지 않음"

다음과 같은 단정은 피할 것

- "~ 때문에 반드시 상승함"
- "~ 때문에 하락함"
- "~가 원인임"
- "~를 증명함"

단, 뉴스 기사에서 원인이 명시적으로 보도된 경우에는
"언론에서는 ~를 원인으로 설명하고 있음"처럼 출처의 해석으로 귀속할 것


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

- 성장주 선호 환경
- 위험자산 선호 환경
- 방어적 접근이 필요한 환경
- 시장 전체보다 주도 섹터의 상대강도를 관찰할 필요
- 시장 폭 개선 여부를 확인할 필요
- 신용시장 악화 여부를 모니터링할 필요

처럼 현재 국면에 대한 포트폴리오 관점의 해석과 관찰 포인트를 제시할 것

반드시 퀀트 데이터와 뉴스의 교차검증 결과를 근거로 판단할 것


8. 문체 및 포맷

- 인사말과 불필요한 서론은 작성하지 말 것
- 마크다운 볼드체 기호 사용 금지
- 정확히 아래 4개의 소제목만 사용할 것
- 각 소제목은 2~3문장으로 작성할 것
- 각 문장은 하나의 핵심 주장만 담을 것
- 한 문장에 여러 근거를 억지로 연결하지 말 것
- 문장 끝에는 온점 등 일반적인 문장 부호를 사용하지 말 것
- 모든 문장은 간결한 명사형으로 종결할 것
- "~함", "~임", "~필요", "~관찰", "~확인", "~유지", "~확대", "~축소" 등의 형태를 사용할 것
- 이모지는 소제목에만 사용할 것


9. 반드시 다음 구조로 작성할 것

🌐 매크로 및 시장 국면 진단

추세, 모멘텀, 금리, 유동성, 매크로 데이터를 종합하여 현재 시장의 본질적인 국면을 진단할 것

가장 결정적인 퀀트 지표 하나를 숫자로 제시하고
나머지 지표는 방향성 중심으로 설명할 것

뉴스에서 언급된 거시경제 이슈가 있다면
퀀트 데이터와 실제로 부합하는지 교차검증할 것


🔥 펀더멘털 및 내러티브 흐름

주도 섹터와 최근 뉴스의 공통 테마를 연결할 것

섹터 데이터가 실제로 보여주는 상대강도와 뉴스에서 제기되는 촉매를 구분할 것

확인되지 않은 스마트 머니 또는 기관 자금 흐름을 임의로 만들어내지 말 것


⚠️ 시스템 리스크 및 꼬리 위험

시장 확산도, 신용, 유동성, 변동성, 지정학적 뉴스 등을 종합할 것

현재 실제로 확인되는 위험과 아직 가능성 수준에 머무는 위험을 구분할 것

시장 폭 축소와 같은 경고 신호가 있다면 설명하되
단일 지표만으로 버블이나 시장 붕괴를 확정하지 말 것

가장 중요한 위험 지표 하나를 숫자로 제시할 것


🎯 퀀트-뉴스 융합 시장 해석

퀀트와 뉴스의 교차검증 결과를 종합하여
현재 시장을 하나의 일관된 국면으로 설명할 것

시장 강도와 내부 취약성을 동시에 표현할 것

향후 판단에서 중요하게 관찰해야 할 변화가 무엇인지 제시할 것

직접적인 매수·매도 지시나 특정 자산의 구체적인 비중 조절 지시는 하지 말 것


10. 최종 출력 형식

반드시 위의 4개 소제목만 출력할 것

각 소제목 아래 2~3문장 작성할 것

전체적으로 8~12문장 사이로 작성할 것

마지막에는 반드시 아래 문구를 그대로 출력할 것

※ 본 분석은 퀀트 데이터 및 언론 보도 기반의 참고용이며, 투자 권유 아님
""",
                rawDataJson,
                newsData
        );

        return callGemini(prompt,flashModel);
    }
}