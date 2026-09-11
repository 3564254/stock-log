package com.stocklog.modules.market.analysis;

import com.stocklog.modules.market.analysis.dto.MarketAnalysisResultResponse;
import com.stocklog.modules.market.index.dto.IndexDashboardDto;
import com.stocklog.modules.market.index.dto.IndexDto;
import com.stocklog.modules.market.index.dto.vo.IndexChartData;
import com.stocklog.modules.market.index.entity.MarketIndex;
import com.stocklog.modules.market.index.entity.MarketIndexHistory;
import com.stocklog.modules.market.index.repository.MarketIndexHistoryRepository;
import com.stocklog.modules.market.index.repository.MarketIndexRepository;
import com.stocklog.modules.market.index.type.IndexType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 퀀트 매크로 모델 수준으로 업그레이드된 시장 분석 서비스입니다.
 * 감점뿐 아니라 강세장(유동성 확대, 인플레 완화 등)에서의 가점 로직이 입체적으로 추가되었습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketAnalysisService {

    private static final int NEUTRAL_SCORE = 50;

    private final MarketIndexRepository marketIndexRepository;
    private final MarketIndexHistoryRepository marketIndexHistoryRepository;


    public MarketAnalysisResultResponse analyze(IndexDashboardDto data) {
        Map<IndexType, List<Double>> indexHistory = new HashMap<>();
        return null;
    }


    // TODO: 절대적인 숫자만 비교하고 추세로는 판단하지 않고 있음
    public MarketAnalysisResultResponse calculateMarketDiagnosis(IndexDashboardDto data) {
        Map<IndexType, Double> latest = marketIndexRepository.findAll().stream()
                .filter(index -> index.getIndexType() != null && index.getCurrentValue() != null)
                .collect(Collectors.toMap(MarketIndex::getIndexType,
                        index -> index.getCurrentValue().doubleValue(), (first, ignored) -> first));

        int score = NEUTRAL_SCORE;
        score += evaluateLiquidity(latest);
        score += evaluateInflation(latest);
        score += evaluateFundamentals(latest);
        score += evaluateRiskAndSentiment(latest);

        int boundedScore = Math.max(0, Math.min(100, score));
        String sentiment = determineMarketSentiment(boundedScore);

        boolean isSahmRuleTriggered = isSahmRuleTriggered();
        String verdict = generateMacroVerdict(data, sentiment, boundedScore, isSahmRuleTriggered);

        return new MarketAnalysisResultResponse(boundedScore, sentiment, verdict);
    }

    private String generateMacroVerdict(IndexDashboardDto data, String marketSentiment, int fearAndGreedScore, boolean isSahmRuleTriggered) {
        String phase = normalizePhase(marketSentiment);
        Double vix = latestValue(data == null ? null : data.vix());
        Double yieldCurve = latestValue(data == null ? null : data.yieldCurve10y2y());
        Double unemployment = latestValue(data == null ? null : data.unemploymentRate());

        boolean highVix = vix != null && vix > 30.0;
        boolean invertedYieldCurve = yieldCurve != null && yieldCurve < 0.0;
        boolean unemploymentRising = isUnemploymentRising(data == null ? null : data.unemploymentRate());

        StringBuilder sb = new StringBuilder();

        sb.append("<div style='font-size:18px; font-weight:800; color:var(--text-dark, #1e293b); margin-bottom:12px;'>");
        sb.append(getMacroFactBriefing(phase, fearAndGreedScore, highVix, invertedYieldCurve, isSahmRuleTriggered));
        sb.append("</div>");

        sb.append("<div style='font-size:14.5px; line-height:1.6; color:var(--text-secondary, #475569); margin-bottom:20px;'>");
        appendSituationAnalysis(sb, phase, fearAndGreedScore, vix, yieldCurve, unemployment,
                highVix, invertedYieldCurve, unemploymentRising, isSahmRuleTriggered);
        sb.append("</div>");

        sb.append("<div style='background:var(--bg-light, #f8fafc); padding:20px; border-radius:12px; border:1px solid var(--border-color, #e2e8f0); box-shadow:0 2px 8px rgba(0,0,0,0.02);'>");
        appendActionBox(sb, phase, isSahmRuleTriggered);
        sb.append("</div>");

        return sb.toString();
    }

    // ── 1. 유동성 평가 (달러 약세, 금리 인하 등에 대한 가점 부여) ──
    private int evaluateLiquidity(Map<IndexType, Double> latest) {
        int points = 0;

        Double dollarRoc = rateOfChange(IndexType.DOLLAR_INDEX, 20);
        if (dollarRoc != null) {
            if (dollarRoc >= 2.0) points -= 6;
            else if (dollarRoc >= 1.0) points -= 3;
            else if (dollarRoc <= -2.0) points += 5; // 강한 달러 약세 (유동성/위험선호 개선)
            else if (dollarRoc <= -1.0) points += 2;
        }

        Double fedFundsRate = latest.get(IndexType.US_FED_FUNDS_RATE);
        if (fedFundsRate != null) {
            if (fedFundsRate >= 5.25) points -= 6;
            else if (fedFundsRate >= 4.5) points -= 4;
            else if (fedFundsRate <= 2.5) points += 6; // 완화적 금리 환경
            else if (fedFundsRate <= 3.5) points += 3;
        }

        Double m2Roc = rateOfChange(IndexType.US_M2, 3);
        if (m2Roc != null) {
            if (m2Roc >= 1.0) points += 6;           // 시중 통화량 강한 팽창
            else if (m2Roc >= 0.5) points += 3;
            else if (m2Roc <= -0.5) points -= 4;
        }
        return points;
    }

    // ── 2. 물가 평가 (인플레 압력 완화 시 가점 대폭 상향) ──
    private int evaluateInflation(Map<IndexType, Double> latest) {
        int points = inflationPoints(IndexType.US_CPI) + inflationPoints(IndexType.US_CORE_PCE);

        // WTI 유가: 단기 급등은 비용 인플레 우려, 하락은 안도감 제공
        Double wtiRoc = rateOfChange(IndexType.WTI_CRUDE_OIL, 20);
        if (wtiRoc != null) {
            if (wtiRoc >= 15.0) points -= 5;
            else if (wtiRoc >= 8.0) points -= 2;
            else if (wtiRoc <= -10.0) points += 3; // 에너지 가격 안정으로 인플레 우려 완화
        }

        return points;
    }

    private int inflationPoints(IndexType type) {
        Double monthlyChange = rateOfChange(type, 1);
        if (monthlyChange == null) return 0;
        if (monthlyChange <= 0.10) return 5;  // 골디락스 수준의 완벽한 물가 안정
        if (monthlyChange <= 0.20) return 3;
        if (monthlyChange >= 0.50) return -7; // 물가 재점화 우려
        if (monthlyChange >= 0.40) return -4;
        return 0;
    }

    // ── 3. 기초 체력 평가 (견조한 경제성장 및 고용안정에 점수 리워드) ──
    private int evaluateFundamentals(Map<IndexType, Double> latest) {
        int points = 0;

        if (isSahmRuleTriggered()) {
            points -= 20; // 🚨 강력한 경기 침체 시그널 (예외 없는 패널티)
        } else {
            Double unemployment = latest.get(IndexType.US_UNEMPLOYMENT_RATE);
            Double unemploymentAverage = movingAverage(IndexType.US_UNEMPLOYMENT_RATE, 3);
            if (unemployment != null && unemploymentAverage != null) {
                double difference = unemployment - unemploymentAverage;
                if (difference >= 0.30) points -= 10;
                else if (difference >= 0.15) points -= 5;
                else if (difference <= -0.15) points += 4; // 실업률 감소 (강력한 고용)
                else if (difference <= -0.05) points += 2;
            }
        }

        Double claimsRocVsAverage = percentageDifferenceFromAverage(IndexType.US_INITIAL_CLAIMS, 4);
        if (claimsRocVsAverage != null) {
            if (claimsRocVsAverage >= 15.0) points -= 8;
            else if (claimsRocVsAverage >= 5.0) points -= 4;
            else if (claimsRocVsAverage <= -10.0) points += 4; // 실업수당 청구 급감 (고용 호조)
        }

        Double gdpRoc = rateOfChange(IndexType.US_GDP, 1);
        if (gdpRoc != null) {
            if (gdpRoc < 0.0) points -= 10;
            else if (gdpRoc < 1.0) points -= 3;
            else if (gdpRoc >= 3.0) points += 6; // 서프라이즈 경제 성장
            else if (gdpRoc >= 2.0) points += 3;
        }

        Double retailSalesRoc = rateOfChange(IndexType.US_RETAIL_SALES, 1);
        if (retailSalesRoc != null) {
            if (retailSalesRoc >= 0.80) points += 5; // 강력한 소비 여력
            else if (retailSalesRoc <= -0.50) points -= 4;
        }

        return points;
    }

    // ── 4. 리스크 및 심리 평가 (하이일드스프레드 완화, VIX 하락 시 점수 리워드) ──
    private int evaluateRiskAndSentiment(Map<IndexType, Double> latest) {
        int points = 0;

        // 1. 장단기 금리차 (수익률 곡선 정상화 시 가점)
        Double yieldCurve = latest.get(IndexType.T10Y2Y);
        if (yieldCurve != null) {
            if (yieldCurve < -0.5) points -= 8;
            else if (yieldCurve < 0.0) points -= 4;
            else if (yieldCurve > 1.0) points += 5; // 경기 호황을 반영하는 정상 곡선
        }
        if (isRapidSteepeningFromInversion()) points -= 8; // 역전 후 급격한 스티프닝은 침체 선행지표

        // 2. VIX (변동성 극단적 축소 시 가점)
        Double vix = latest.get(IndexType.VIX);
        if (vix != null) {
            if (vix > 30.0) points -= 12;
            else if (vix > 22.0) points -= 6;
            else if (vix < 13.0) points += 7; // 완벽한 리스크-온 시장 분위기
            else if (vix < 16.0) points += 3;

            Double vixAvg = movingAverage(IndexType.VIX, 50);
            if (vixAvg != null && vixAvg > 0) {
                double vixSpike = (vix - vixAvg) / vixAvg * 100.0;
                if (vixSpike >= 40.0) points -= 8; // 단기 발작 시 추가 패널티
            }
        }

        // 3. 하이일드 스프레드 (절대적 수준으로 신용 리스크 측정)
        Double highYieldSpread = latest.get(IndexType.HIGH_YIELD_SPREAD);
        if (highYieldSpread != null) {
            if (highYieldSpread >= 5.5) points -= 10; // 신용 경색 위험
            else if (highYieldSpread >= 4.5) points -= 5;
            else if (highYieldSpread <= 3.5) points += 6; // 풍부한 위험 선호도
        }

        // 4. 금융스트레스 지수 (STLFSI4) - IndexType에 추가된 지표 활용
        Double fsi = latest.get(IndexType.FINANCIAL_STRESS);
        if (fsi != null) {
            if (fsi >= 1.5) points -= 12;      // 극심한 금융 스트레스
            else if (fsi >= 0.0) points -= 4;  // 평균 이상
            else if (fsi <= -0.5) points += 5; // 매우 완화된 금융 시장 여건
        }

        return points;
    }

    // ==========================================
    // 💡 샴 법칙 (Sahm Rule) 계산기 및 포맷팅/유틸 메서드 (변경 없음)
    // ==========================================
    private boolean isSahmRuleTriggered() {
        List<Double> unemp = values(IndexType.US_UNEMPLOYMENT_RATE);
        if (unemp.size() < 12) return false;

        double recent3mAvg = unemp.subList(unemp.size() - 3, unemp.size()).stream()
                .mapToDouble(Double::doubleValue).average().orElse(0.0);

        double past12mLow = Double.MAX_VALUE;
        for (int i = unemp.size() - 12; i < unemp.size(); i++) {
            past12mLow = Math.min(past12mLow, unemp.get(i));
        }

        return (recent3mAvg - past12mLow) >= 0.50;
    }

    private String getMacroFactBriefing(String phase, int score, boolean highVix,
                                        boolean invertedYieldCurve, boolean isSahmRuleTriggered) {
        if (isSahmRuleTriggered) return "<span style='color:var(--toss-red);'>🚨 샴 법칙 발동:</span> <b>실물 경기 침체(Recession) 임박 국면</b>";
        if (highVix) return "변동성 급등이 포착된 <b>고위험 경계 국면</b>";
        if (invertedYieldCurve) return "장단기 금리 역전이 지속되는 <b>경기침체 경보 국면</b>";

        return switch (phase) {
            case "EXTREME_FEAR" -> "공포가 극대화된 <b>패닉·투매 가능성 구간</b>";
            case "FEAR" -> "방어 심리가 우세한 <b>리스크오프 구간</b>";
            case "GREED" -> "유동성과 위험선호가 개선된 <b>리스크온 구간</b>";
            case "EXTREME_GREED" -> "낙관론이 과도하게 확산된 <b>과열 경계 구간</b>";
            default -> "상·하방 신호가 공존하는 <b>방향 탐색 구간</b> (공포·탐욕 점수 " + score + "점)";
        };
    }

    private void appendSituationAnalysis(StringBuilder sb, String phase, int score, Double vix,
                                         Double yieldCurve, Double unemployment, boolean highVix,
                                         boolean invertedYieldCurve, boolean unemploymentRising, boolean isSahmRuleTriggered) {
        switch (phase) {
            case "EXTREME_FEAR" -> sb.append("시장 참여자의 위험 회피가 한꺼번에 집중된 국면입니다. 가격 변동은 커질 수 있으나, 장기 투자자에게는 우량 지수자산의 기대수익률이 개선될 수 있는 구간입니다.");
            case "FEAR" -> sb.append("매크로 불확실성이 위험자산의 할인율을 높이며 방어적 포지션이 선호되는 국면입니다. 반등이 나오더라도 추세 전환으로 단정하기보다 위험 신호의 완화를 확인해야 합니다.");
            case "GREED" -> sb.append("성장·유동성 환경이 위험자산을 지지하며 투자 심리가 개선된 국면입니다. 다만 긍정적인 서사가 과도한 추격 매수로 이어지지 않도록 밸류에이션과 변동성을 함께 점검해야 합니다.");
            case "EXTREME_GREED" -> sb.append("낙관론과 FOMO가 확산되어 위험 프리미엄이 낮아진 과열 국면입니다. 상승 추세가 이어질 수 있어도 신규 진입의 손익비는 악화될 수 있으므로 수익 보호가 우선입니다.");
            default -> sb.append("물가·고용·금융여건의 방향이 엇갈리는 중립 국면입니다. 공포·탐욕 점수 ").append(score).append("점은 시장이 뚜렷한 방향성을 확신하지 못하고 있음을 의미합니다.");
        }

        if (isSahmRuleTriggered) {
            sb.append("<br><br>🚨 <b>강력한 경기 침체 경고 (샴 법칙):</b> 최근 3개월 실업률 평균이 과거 12개월 최저치보다 0.5%p 이상 급등했습니다. 역사적으로 이 신호가 켜졌을 때 미국 경제는 예외 없이 침체에 돌입했습니다. 실적 하향과 유동성 축소 위험에 대비해야 합니다.");
        } else if (unemploymentRising) {
            sb.append("<br>⚠️ <b>고용시장 냉각:</b> 실업률이 최근 추세 대비 상승하고 있습니다").append(unemployment == null ? "." : " (최근 " + format(unemployment) + "%).").append(" 소비 둔화와 기업 이익 전망 하향으로 이어지는지 확인하십시오.");
        }

        if (highVix) {
            sb.append("<br>⚠️ <b>극단적 변동성:</b> VIX가 <b>").append(format(vix)).append("</b>로 30을 상회했습니다. 단기 급등락과 강제 청산 가능성이 높으므로 레버리지와 추격 매수는 자제해야 합니다.");
        }

        if (invertedYieldCurve) {
            sb.append("<br>⚠️ <b>수익률곡선 역전:</b> 10년-2년 스프레드가 <b>").append(format(yieldCurve)).append("%p</b>입니다. 통화긴축의 시차 효과와 향후 경기침체 위험을 포트폴리오에 반영할 필요가 있습니다.");
        }
    }

    private void appendActionBox(StringBuilder sb, String phase, boolean isSahmRuleTriggered) {
        sb.append("<div style='display:flex; align-items:center; font-size:15.5px; font-weight:700; color:var(--text-dark, #1e293b); margin-bottom:10px;'>");

        if (isSahmRuleTriggered) {
            sb.append("<span style='margin-right:6px;'>🚨</span> <b>극단적 방어 및 현금 확보 가이드</b></div>");
            sb.append("<div style='font-size:14px; color:var(--text-secondary, #475569); line-height:1.6;'>");
            sb.append("펀더멘털의 급격한 악화가 진행 중입니다. 주식 비중을 최소화하고, <b>국채(안전 자산) 및 달러/현금 비중을 포트폴리오의 50% 이상으로 확대</b>하십시오. 낙폭 과대에 따른 단기 반등(Dead Cat Bounce)에 속지 마시고, 연준의 대규모 금리 인하가 시장에 완전히 소화될 때까지 관망해야 합니다.");
            sb.append("</div>");
            return;
        }

        switch (phase) {
            case "EXTREME_FEAR" -> {
                sb.append("<span style='margin-right:6px;'>🛒</span> <b>저점 분할 매수 및 축적 가이드</b></div>");
                sb.append("<div style='font-size:14px; color:var(--text-secondary, #475569); line-height:1.6;'>");
                sb.append("패닉성 투매가 일어나는 구간으로, 역발상 투자가 유효한 시점입니다. VIX 급등 시마다 <b>S&P 500 등 우량 지수 추종 자산을 주 단위로 분할 매수</b>하십시오. 당장의 손실을 두려워하기보다 1~2년 뒤의 기대 수익률을 챙겨야 할 때입니다.");
                sb.append("</div>");
            }
            case "FEAR" -> {
                sb.append("<span style='margin-right:6px;'>🛡️</span> <b>방어 우선 하방 경직성 확보 가이드</b></div>");
                sb.append("<div style='font-size:14px; color:var(--text-secondary, #475569); line-height:1.6;'>");
                sb.append("성장주 비중을 줄이고 <b>배당주, 헬스케어, 필수소비재 등 방어주와 투자등급 회사채 비중을 높여</b> 포트폴리오의 변동성을 낮추십시오. 신규 자금 투입은 보류하고 시장의 지지선(Support Level)이 확인될 때까지 대기하는 것이 유리합니다.");
                sb.append("</div>");
            }
            case "GREED" -> {
                sb.append("<span style='margin-right:6px;'>📈</span> <b>리스크온(Risk-on) 트렌드 팔로잉 가이드</b></div>");
                sb.append("<div style='font-size:14px; color:var(--text-secondary, #475569); line-height:1.6;'>");
                sb.append("우호적 매크로 환경이 훼손되지 않았으므로 <b>위험자산(주식) 비중을 70% 이상 유지하며 수익을 극대화</b>하십시오. 추세를 거스르는 인버스(숏) 투자는 삼가며, 분기별 1회 정도의 리밸런싱만으로 포트폴리오 쏠림을 관리하는 것이 좋습니다.");
                sb.append("</div>");
            }
            case "EXTREME_GREED" -> {
                sb.append("<span style='margin-right:6px;'>💰</span> <b>과열 국면 수익 실현 및 리스크 관리 가이드</b></div>");
                sb.append("<div style='font-size:14px; color:var(--text-secondary, #475569); line-height:1.6;'>");
                sb.append("시장에 FOMO(소외 불안감)가 팽배한 단기 고점 징후가 있습니다. 신규 진입은 철저히 자제하고, <b>수익이 크게 난 자산은 20~30% 분할 매도하여 현금이나 단기채(MMF)로 전환</b>해 다가올 변동성 장세에 대비할 실탄을 마련하십시오.");
                sb.append("</div>");
            }
            default -> {
                sb.append("<span style='margin-right:6px;'>⚖️</span> <b>중립 국면 자산 배분 가이드</b></div>");
                sb.append("<div style='font-size:14px; color:var(--text-secondary, #475569); line-height:1.6;'>");
                sb.append("상하방 재료가 혼재된 방향성 탐색 구간입니다. <b>주식 60 : 채권 40의 정석적인 자산배분 비율을 유지</b>하는 것을 권장합니다. 다음 달 발표될 CPI(소비자물가)와 고용보고서 결과에 따라 방향이 결정될 가능성이 높습니다.");
                sb.append("</div>");
            }
        }
    }

    private boolean isRapidSteepeningFromInversion() {
        List<Double> values = values(IndexType.T10Y2Y);
        if (values.size() < 4) return false;

        double current = values.get(values.size() - 1);
        double threePeriodsAgo = values.get(values.size() - 4);
        return threePeriodsAgo < 0.0 && current - threePeriodsAgo >= 0.50;
    }

    private Double movingAverage(IndexType type, int period) {
        List<Double> values = values(type);
        if (values.size() < period) return null;
        return values.subList(values.size() - period, values.size()).stream()
                .mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    private Double percentageDifferenceFromAverage(IndexType type, int period) {
        List<Double> values = values(type);
        if (values.size() < period + 1) return null;
        double current = values.get(values.size() - 1);
        double previousAverage = values.subList(values.size() - period - 1, values.size() - 1).stream()
                .mapToDouble(Double::doubleValue).average().orElseThrow();
        if (previousAverage == 0.0) return null;
        return (current - previousAverage) / previousAverage * 100.0;
    }

    private Double rateOfChange(IndexType type, int periods) {
        List<Double> values = values(type);
        if (values.size() <= periods) return null;
        double current = values.get(values.size() - 1);
        double previous = values.get(values.size() - 1 - periods);
        if (previous == 0.0) return null;
        return (current - previous) / previous * 100.0;
    }

    private Double absoluteChange(IndexType type, int periods) {
        List<Double> values = values(type);
        if (values.size() <= periods) return null;
        return values.get(values.size() - 1) - values.get(values.size() - 1 - periods);
    }

    private List<Double> values(IndexType type) {
        return marketIndexHistoryRepository.findBySymbolOrderByDateAsc(type.getSeriesId()).stream()
                .map(MarketIndexHistory::getCloseValue)
                .filter(value -> value != null)
                .map(BigDecimal::doubleValue)
                .toList();
    }

    private String determineMarketSentiment(int score) {
        if (score >= 70) return "EXTREME_GREED";
        if (score >= 55) return "GREED";
        if (score >= 45) return "NEUTRAL";
        if (score >= 25) return "FEAR";
        return "EXTREME_FEAR";
    }

    private boolean isUnemploymentRising(IndexDto unemploymentRate) {
        List<IndexChartData> data = unemploymentRate == null ? List.of() : unemploymentRate.data();
        if (data == null || data.size() < 4) return false;
        Double current = valueAt(data, data.size() - 1);
        if (current == null) return false;
        double sum = 0;
        for (int i = data.size() - 4; i < data.size() - 1; i++) {
            Double value = valueAt(data, i);
            if (value == null) return false;
            sum += value;
        }
        return current - (sum / 3) >= 0.15;
    }

    private Double latestValue(IndexDto index) {
        if (index == null || index.data() == null || index.data().isEmpty()) return null;
        return valueAt(index.data(), index.data().size() - 1);
    }

    private Double valueAt(List<IndexChartData> data, int index) {
        BigDecimal value = data.get(index) == null ? null : data.get(index).getValue();
        return value == null ? null : value.doubleValue();
    }

    private String normalizePhase(String marketPhase) {
        if (marketPhase == null) return "NEUTRAL";
        return switch (marketPhase) {
            case "EXTREME_FEAR", "FEAR", "NEUTRAL", "GREED", "EXTREME_GREED" -> marketPhase;
            default -> "NEUTRAL";
        };
    }

    private String format(Double value) {
        return String.format("%.2f", value);
    }
}