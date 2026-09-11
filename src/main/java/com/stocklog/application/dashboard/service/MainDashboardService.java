package com.stocklog.application.dashboard.service;

import com.stocklog.application.dashboard.MainDashboardResponse;
import com.stocklog.application.dashboard.usecase.MainDashboardUseCase;
import com.stocklog.modules.purchase.portfolio.dto.SectorWeightDto;
import com.stocklog.modules.purchase.portfolio.dto.response.PortfolioAnalysisResponse;
import com.stocklog.modules.purchase.portfolio.dto.response.PortfolioResponse;
import com.stocklog.modules.purchase.portfolio.service.PortfolioFacadeService;
import com.stocklog.modules.purchase.trade.dto.vo.WatchItemDto;
import com.stocklog.modules.purchase.trade.service.TradeFacadeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MainDashboardUseCase의 구현체 자리.
 *
 * TODO(구현): MainController가 직접 조합하는 포트폴리오, 분석, 섹터, 관심 종목 데이터를
 * 하나의 화면 모델로 만들고 Controller는 해당 결과만 Model에 전달하게 한다.
 */
@Service
@RequiredArgsConstructor
public class MainDashboardService implements MainDashboardUseCase {

    private final PortfolioFacadeService portfolioService;
    private final TradeFacadeService tradeService;

    @Override
    public MainDashboardResponse getDashboardData(Long memberId) {
        if (memberId == null) return MainDashboardResponse.createFallback();
        PortfolioResponse myPortfolio = portfolioService.getMyPortfolio(memberId);
        PortfolioAnalysisResponse portfolioAnalysisResponse = portfolioService.analyzePortfolio(myPortfolio);
        List<SectorWeightDto> sectorWeights = portfolioService.getSectorWeights(myPortfolio);
        List<WatchItemDto> watchItemDtos = tradeService.getWatchlist(memberId).itemResponseList();

        return MainDashboardResponse.builder()
                .portfolioResponse(myPortfolio)
                .portfolioAnalysisResponse(portfolioAnalysisResponse)
                .sectorWeightDtoList(sectorWeights)
                .watchListResponse(watchItemDtos)
                .build();
    }

}
