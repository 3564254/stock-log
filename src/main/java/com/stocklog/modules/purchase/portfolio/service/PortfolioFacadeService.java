package com.stocklog.modules.purchase.portfolio.service;

import com.stocklog.modules.purchase.portfolio.dto.SectorWeightDto;
import com.stocklog.modules.purchase.portfolio.dto.request.HoldingUpdateRequest;
import com.stocklog.modules.purchase.portfolio.dto.response.PortfolioAnalysisResponse;
import com.stocklog.modules.purchase.portfolio.dto.response.PortfolioResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PortfolioFacadeService {
    private final PortfolioService portfolioService;
    private final PortfolioAnalysisService portfolioAnalysisService;

    // ================= Portfolio Service  ================= //

    public PortfolioResponse getMyPortfolio(Long memberId) {
        return portfolioService.getMyPortfolio(memberId);
    }

    public void updateHolding(Long memberId, HoldingUpdateRequest request) {
        portfolioService.updateHolding(memberId, request);
    }

    public void deleteHolding(Long memberId, String stockCode) {
        portfolioService.deleteHolding(memberId, stockCode);
    }

    // ================= Portfolio Analysis ================= //
    public PortfolioAnalysisResponse analyzePortfolio(Long memberId) {
        PortfolioResponse portfolio = getMyPortfolio(memberId);
        return portfolioAnalysisService.analyzePortfolio(portfolio);
    }

    public PortfolioAnalysisResponse analyzePortfolio(PortfolioResponse portfolio) {
        return portfolioAnalysisService.analyzePortfolio(portfolio);
    }

    public BigDecimal calculateTotalScore(Long memberId, BigDecimal sectorDiversityScore, BigDecimal winRate) {
        PortfolioResponse portfolio = getMyPortfolio(memberId);
        return portfolioAnalysisService.calculateTotalScore(portfolio, sectorDiversityScore, winRate);
    }

    public List<SectorWeightDto> getSectorWeights(Long memberId) {
        PortfolioResponse portfolio = getMyPortfolio(memberId);
        return portfolioAnalysisService.getSectorWeights(portfolio);
    }

    public List<SectorWeightDto> getSectorWeights(PortfolioResponse portfolio) {
        return portfolioAnalysisService.getSectorWeights(portfolio);
    }


}
