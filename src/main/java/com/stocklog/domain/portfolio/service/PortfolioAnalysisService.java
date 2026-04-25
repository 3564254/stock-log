package com.stocklog.domain.portfolio.service;

import com.stocklog.domain.portfolio.dto.response.HoldingResponse;
import com.stocklog.domain.portfolio.dto.response.PortfolioAnalysisResponse;
import com.stocklog.domain.portfolio.dto.response.PortfolioResponse;
import com.stocklog.domain.portfolio.entity.PortfolioGrade;
import com.stocklog.domain.stock.entity.Stock;
import com.stocklog.domain.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PortfolioAnalysisService {

    public PortfolioAnalysisResponse analyzePortfolio(PortfolioResponse portfolio) {
        BigDecimal totalAssets = portfolio.totalAssets();

        // 방어 로직: 자산이 0인 경우
        if (totalAssets == null || totalAssets.compareTo(BigDecimal.ZERO) == 0) {
            return PortfolioAnalysisResponse.builder()
                    .totalAssets(BigDecimal.ZERO)
                    .hhi(BigDecimal.ZERO)
                    .totalScore(BigDecimal.ZERO)
                    .build();
        }

        // 1. 핵심 지표 계산
        BigDecimal hhi = calculateHHI(portfolio);
        BigDecimal winRate = calculateWinRate(portfolio);
        BigDecimal totalScore = calculateTotalScore(portfolio, hhi, winRate);

        // 2. 등급 및 인사이트 도출
        PortfolioGrade gradeInfo = PortfolioGrade.of(totalScore);
        // String personalizedInsight = generateInsightMessage(portfolio, gradeInfo);

        // 3. 차트용 데이터 추출
        List<String> labels = portfolio.myStocks().stream()
                .map(HoldingResponse::korName)
                .toList();

        List<BigDecimal> data = portfolio.myStocks().stream()
                .map(HoldingResponse::getCurrentValueKrw)
                .toList();

        return PortfolioAnalysisResponse.builder()
                .totalAssets(totalAssets)
                .hhi(hhi)
                .winRate(winRate)
                .totalScore(totalScore)
                .grade(gradeInfo.getGradeName())
                .insightMessage(gradeInfo.getInsightMessage())
                .gradeColor(gradeInfo.getColorCode())
                .chartLabels(labels)
                .chartData(data)
                .build();
    }


    public BigDecimal calculateHHI(PortfolioResponse portfolio) {
        BigDecimal hhi = BigDecimal.ZERO;
        BigDecimal total = portfolio.totalAssets();

        for (HoldingResponse stock : portfolio.myStocks()) {
            BigDecimal stockTotalValue = stock.getCurrentValueKrw();

            BigDecimal weight = stockTotalValue
                    .divide(total, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            // [핵심] 순수 지수형 ETF는 이미 고도로 분산되어 있으므로 패널티 완화
            if ("순수 지수형 ETF".equals(stock.sector())) {
                weight = weight.divide(BigDecimal.valueOf(5), 2, RoundingMode.HALF_UP);
            }

            BigDecimal weightSquared = weight.pow(2);
            hhi = hhi.add(weightSquared);
        }
        return hhi.setScale(0, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateWinRate(PortfolioResponse portfolio) {
        long totalStocks = portfolio.myStocks().size();
        if (totalStocks == 0) return BigDecimal.ZERO;

        long profitableStocks = portfolio.myStocks().stream()
                .filter(stock -> stock.profitRate().compareTo(BigDecimal.ZERO) >= 0)
                .count();

        return BigDecimal.valueOf(profitableStocks)
                .divide(BigDecimal.valueOf(totalStocks), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateTotalScore(PortfolioResponse portfolio, BigDecimal hhi, BigDecimal winRate) {
        // [1] HHI 점수 (Max 30)
        BigDecimal hhiScore = (hhi.compareTo(new BigDecimal("1500")) < 0) ? BigDecimal.valueOf(30) :
                (hhi.compareTo(new BigDecimal("2500")) < 0) ? BigDecimal.valueOf(20) :
                        (hhi.compareTo(new BigDecimal("4000")) < 0) ? BigDecimal.valueOf(10) : BigDecimal.valueOf(5);

        // [2] 수익률 점수 (Max 40)
        BigDecimal returnRate = portfolio.totalReturnRate();
        BigDecimal returnScore = (returnRate.compareTo(new BigDecimal("15")) >= 0) ? BigDecimal.valueOf(40) :
                (returnRate.compareTo(new BigDecimal("5")) >= 0)  ? BigDecimal.valueOf(30) :
                        (returnRate.compareTo(new BigDecimal("0")) >= 0)  ? BigDecimal.valueOf(20) : BigDecimal.valueOf(10);

        // [3] 승률 점수 (Max 30)
        BigDecimal winRateScore = (winRate.compareTo(new BigDecimal("70")) >= 0) ? BigDecimal.valueOf(30) :
                (winRate.compareTo(new BigDecimal("50")) >= 0) ? BigDecimal.valueOf(20) :
                        (winRate.compareTo(new BigDecimal("30")) >= 0) ? BigDecimal.valueOf(10) : BigDecimal.valueOf(5);

        return hhiScore.add(returnScore).add(winRateScore);
    }



}
