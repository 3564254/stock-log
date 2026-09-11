package com.stocklog.modules.purchase.portfolio.service;

import com.stocklog.modules.purchase.portfolio.dto.SectorWeightDto;
import com.stocklog.modules.purchase.portfolio.dto.response.HoldingResponse;
import com.stocklog.modules.purchase.portfolio.dto.response.PortfolioAnalysisResponse;
import com.stocklog.modules.purchase.portfolio.dto.response.PortfolioResponse;
import com.stocklog.modules.purchase.portfolio.entity.PortfolioGrade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class PortfolioAnalysisService {

    PortfolioAnalysisResponse analyzePortfolio(PortfolioResponse portfolio) {
        // 방어 로직: 보유 종목이 없는 경우
        if (portfolio.myStocks().isEmpty()) {
            return PortfolioAnalysisResponse.builder()
                    .hhi(BigDecimal.ZERO)
                    .winRate(BigDecimal.ZERO)
                    .totalScore(BigDecimal.ZERO)
                    .build();
        }

        // 1. 핵심 전략 지표 계산
        BigDecimal winRate = calculateWinRate(portfolio);
        BigDecimal sectorDiversityScore = calculateSectorDiversityScore(portfolio); // HHI 대체
        BigDecimal totalScore = calculateTotalScore(portfolio, sectorDiversityScore, winRate);

        // 2. 전략 등급 및 인사이트 도출
        PortfolioGrade gradeInfo = PortfolioGrade.of(totalScore);

        // 3. 차트용 데이터 추출 (종목 개수 기반 섹터 분산도)
        Map<String, Long> sectorCounts = portfolio.myStocks().stream()
                .collect(Collectors.groupingBy(HoldingResponse::sector, Collectors.counting()));

        List<String> labels = sectorCounts.keySet().stream().toList();
        List<BigDecimal> data = sectorCounts.values().stream()
                .map(BigDecimal::valueOf)
                .toList();

        return PortfolioAnalysisResponse.builder()
                .hhi(BigDecimal.ZERO) // DTO 호환성을 위해 유지하되 0으로 고정 (또는 DTO에서 제거 권장)
                .winRate(winRate)
                .totalScore(totalScore)
                .grade(gradeInfo.getGradeName())
                .insightMessage(gradeInfo.getInsightMessage())
                .gradeColor(gradeInfo.getColorCode())
                .chartLabels(labels)
                .chartData(data)
                .build();
    }

    // 종목 수 대비 승률 (수익 중인 종목 비율)
    private BigDecimal calculateWinRate(PortfolioResponse portfolio) {
        long totalStocks = portfolio.myStocks().size();
        if (totalStocks == 0) return BigDecimal.ZERO;

        long profitableStocks = portfolio.myStocks().stream()
                .filter(stock -> stock.returnRate().compareTo(BigDecimal.ZERO) >= 0)
                .count();

        return BigDecimal.valueOf(profitableStocks)
                .divide(BigDecimal.valueOf(totalStocks), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    // 자산 비중 HHI를 대체하는 '섹터 종목수 분산도' 점수 (Max 30점)
    private BigDecimal calculateSectorDiversityScore(PortfolioResponse portfolio) {
        long totalStocks = portfolio.myStocks().size();
        if (totalStocks == 0) return BigDecimal.ZERO;

        Map<String, Long> sectorCounts = portfolio.myStocks().stream()
                .collect(Collectors.groupingBy(HoldingResponse::sector, Collectors.counting()));

        int uniqueSectors = sectorCounts.size();

        // 1개 섹터 몰빵: 5점 / 2개 섹터: 15점 / 3개 이상: 30점 만점
        if (uniqueSectors >= 3) return BigDecimal.valueOf(30);
        if (uniqueSectors == 2) return BigDecimal.valueOf(15);
        return BigDecimal.valueOf(5);
    }

    // 전략 중심의 종합 점수 산출
    BigDecimal calculateTotalScore(PortfolioResponse portfolio, BigDecimal sectorDiversityScore, BigDecimal winRate) {

        // [2] 단순 평균 수익률 점수 (Max 40)
        BigDecimal avgReturnRate = portfolio.averageReturnRate();
        BigDecimal returnScore = (avgReturnRate.compareTo(new BigDecimal("10")) >= 0) ? BigDecimal.valueOf(40) :
                (avgReturnRate.compareTo(new BigDecimal("3")) >= 0) ? BigDecimal.valueOf(30) :
                (avgReturnRate.compareTo(new BigDecimal("0")) >= 0) ? BigDecimal.valueOf(20) : BigDecimal.valueOf(10);

        // [3] 승률 점수 (Max 30)
        BigDecimal winRateScore = (winRate.compareTo(new BigDecimal("70")) >= 0) ? BigDecimal.valueOf(30) :
                (winRate.compareTo(new BigDecimal("50")) >= 0) ? BigDecimal.valueOf(20) :
                (winRate.compareTo(new BigDecimal("30")) >= 0) ? BigDecimal.valueOf(10) : BigDecimal.valueOf(5);

        return sectorDiversityScore.add(returnScore).add(winRateScore);
    }

    // 섹터별 종목 '개수' 기반 비중 (차트 렌더링용)
    List<SectorWeightDto> getSectorWeights(PortfolioResponse portfolio) {
        long totalStocks = portfolio.myStocks().size();
        if (totalStocks == 0) return List.of();

        return portfolio.myStocks().stream()
                .collect(Collectors.groupingBy(HoldingResponse::sector, Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new SectorWeightDto(
                        entry.getKey(),
                        BigDecimal.valueOf(entry.getValue())
                                .divide(BigDecimal.valueOf(totalStocks), 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                ))
                .toList();
    }
}