package com.stocklog.modules.market.analysis.service.analyzer;

import com.stocklog.modules.market.analysis.service.result.MarketAnalyzerScore;
import com.stocklog.modules.market.analysis.service.result.MarketAnalyzer;
import com.stocklog.modules.market.analysis.service.result.ScoreCategory;
import com.stocklog.modules.market.chart.MarketChartData;
import com.stocklog.modules.market.domain.type.IndexType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.stocklog.modules.common.finance.indicator.trend.MomentumIndicators.computeReturn;
import static com.stocklog.modules.market.analysis.service.analyzer.utils.GetDoublesUtils.getDoubles;

@Component
public class CreditAnalyzer implements MarketAnalyzer {

    // 세부 점수 및 다이버전스 경고 여부를 담는 레코드
    public record CreditDetails(
            double hySpreadScore,
            double financialStressScore,
            double consumerLoansScore,
            double businessLoansScore,
            boolean creditDivergence,
            String creditRegime
    ) {}

    @Override
    public MarketAnalyzerScore analyze(Map<IndexType,MarketChartData> data) {
        // 데이터 추출
        List<Double> hySpreadCloses = getDoubles(data, IndexType.HIGH_YIELD_SPREAD);
        List<Double> finStressCloses = getDoubles(data, IndexType.FINANCIAL_STRESS);
        List<Double> loansCloses = getDoubles(data, IndexType.US_CONSUMER_REVOLVING_LOANS);
        List<Double> businessLoansCloses = getDoubles(data, IndexType.SECTOR_FINANCIALS);   // TOTCI: 상업·산업 대출 총액 (주간)

        // 다이버전스 체크용 데이터
        List<Double> sp500Closes = getDoubles(data, IndexType.SP500);
        List<Double> vixCloses = getDoubles(data, IndexType.VIX);

        // 각 팩터별 점수 계산
        double hySpreadScore = analyzeHySpread(hySpreadCloses);
        double financialStressScore = analyzeFinancialStress(finStressCloses);
        double consumerLoansScore = analyzeConsumerLoans(loansCloses);
        double businessLoansScore = analyzeBusinessLoans(businessLoansCloses);

        // 총합 계산 및 -100 ~ +100 정규화
        double totalScore = hySpreadScore + financialStressScore + consumerLoansScore + businessLoansScore;
        totalScore = Math.max(-100.0, Math.min(100.0, totalScore));

        // Credit Divergence 판별
        boolean hasDivergence = detectCreditDivergence(sp500Closes, vixCloses, hySpreadCloses);

        // 국면 판별 (HEALTHY, NEUTRAL, STRESS)
        String regime = determineCreditRegime(totalScore, hasDivergence);

        ScoreCategory category = MarketAnalyzerScore.categorize(totalScore);
        CreditDetails details = new CreditDetails(
                hySpreadScore, financialStressScore, consumerLoansScore, businessLoansScore, hasDivergence, regime
        );

        return new MarketAnalyzerScore("CreditHealth", totalScore, category, details);
    }

    // [팩터 1] High Yield Spread (최대 ±35점) - 낮을수록, 축소될수록 긍정적
    private double analyzeHySpread(List<Double> closes) {
        if (closes == null || closes.size() < 21) return 0.0;
        double score = 0;
        int lastIdx = closes.size() - 1;
        double cur = closes.get(lastIdx);
        double prev5 = closes.get(lastIdx - 5);
        double prev20 = closes.get(lastIdx - 20);

        // 절대 레벨 평가
        if (cur < 4.0) score += 15;
        else if (cur >= 5.0) score -= 15;

        // 변화 방향 평가 (축소 시 긍정적)
        if (cur < prev5) score += 10;
        else score -= 10;

        if (cur < prev20) score += 10;
        else score -= 10;

        return score;
    }

    // [팩터 2] Financial Stress Index (최대 ±30점) - 낮을수록 긍정적
    private double analyzeFinancialStress(List<Double> closes) {
        if (closes == null || closes.size() < 21) return 0.0;
        double score = 0;
        int lastIdx = closes.size() - 1;
        double cur = closes.get(lastIdx);
        double prev20 = closes.get(lastIdx - 20);

        // 0을 기준으로 금융 스트레스 상태 판별
        if (cur < 0) score += 15;
        else score -= 15;

        // 변화 추세
        if (cur < prev20) score += 15;
        else score -= 15;

        return score;
    }

    /** 기업 대출은 주간 데이터라 13주(약 한 분기) 단위로 비교한다 */
    private static final int LOAN_WEEKS = 13;

    // [팩터 3] 기업 대출 증가세 (최대 ±20점) - 은행 신용 공급이 늘면 긍정적
    // 예전에는 TOTCI(대출 총액)를 '금융주'처럼 보고 S&P 500 20일 수익률과 비교했는데,
    // 가격이 아니고 주기도 달라(20칸 = 20주) 비교가 성립하지 않아 대출 자체의 증가세로 바꿨다.
    private double analyzeBusinessLoans(List<Double> loans) {
        if (loans == null || loans.size() < LOAN_WEEKS * 2 + 1) return 0.0;
        double score = 0;

        double recent = computeReturn(loans, loans.size() - 1, LOAN_WEEKS);
        double prior = computeReturn(loans, loans.size() - 1 - LOAN_WEEKS, LOAN_WEEKS);

        // 대출이 늘고 있는지
        if (recent > 0) score += 10;
        else score -= 10;

        // 증가 속도가 빨라지는지 (둔화·위축이면 감점)
        if (recent > prior) score += 10;
        else score -= 10;

        return score;
    }

    // [팩터 4] 소비자 리볼빙 대출 (최대 ±15점) - 완만한 성장 긍정적, 급격한 위축 부정적
    private double analyzeConsumerLoans(List<Double> closes) {
        if (closes == null || closes.size() < 21) return 0.0;
        double score = 0;
        int lastIdx = closes.size() - 1;
        double currentDelta = closes.get(lastIdx) - closes.get(lastIdx - 20);

        // 신용 경색(위축) 여부 확인
        if (currentDelta >= 0) score += 15;
        else score -= 15;

        return score;
    }

    // 핵심: 표면적 Risk-On 상태이나 신용 스프레드가 튀는 Credit Divergence 포착
    private boolean detectCreditDivergence(List<Double> sp500, List<Double> vix, List<Double> hySpread) {
        if (sp500 == null || vix == null || hySpread == null || sp500.size() < 21) return false;

        double sp500Return20 = computeReturn(sp500, sp500.size() - 1, 20);
        double vixReturn20 = computeReturn(vix, vix.size() - 1, 20);
        double hySpreadReturn20 = computeReturn(hySpread, hySpread.size() - 1, 20);

        // S&P500 상승 및 VIX 하락(표면적 Risk-On) 상태 확인
        boolean isSurfaceRiskOn = sp500Return20 > 0 && vixReturn20 <= 0;

        // 20일 동안 하이일드 스프레드가 5% 이상 크게 확대되는 내부 스트레스 발생 여부
        boolean isInternalStress = hySpreadReturn20 > 5.0;

        return isSurfaceRiskOn && isInternalStress;
    }

    private String determineCreditRegime(double totalScore, boolean hasDivergence) {
        // 다이버전스 발생 시 최우선 경고(Warning) 반환
        if (hasDivergence) return "WARNING_DIVERGENCE";
        if (totalScore >= 40) return "HEALTHY";
        if (totalScore >= -20) return "NEUTRAL";
        return "STRESS";
    }
}