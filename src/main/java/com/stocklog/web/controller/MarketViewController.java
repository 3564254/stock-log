package com.stocklog.web.controller;

import com.stocklog.domain.application.StockFacadeService;
import com.stocklog.domain.marketdata.analysis.MarketDataAnalyzer;
import com.stocklog.domain.marketdata.exchange.service.ExchangeService;
import com.stocklog.domain.marketdata.index.dto.IndexDashboardDto;
import com.stocklog.domain.marketdata.index.service.IndexService;
import com.stocklog.domain.marketdata.universe.dto.UniverseDashboardDto;
import com.stocklog.domain.marketdata.universe.dto.UniverseDto;
import com.stocklog.domain.member.dto.response.MemberResponse;
import com.stocklog.domain.marketdata.universe.entity.Universe;
import com.stocklog.domain.marketdata.universe.service.UniverseService;
import com.stocklog.global.annotation.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class MarketViewController {

    private final StockFacadeService facadeService;
    private final MarketDataAnalyzer marketDataAnalyzer;


    @GetMapping("/market")
    public String marketPage(@LoginUser MemberResponse loginMember, Model model) {
        List<UniverseDto> universeDashboard = facadeService.getUniverseDashboard();

        UniverseDashboardDto universeDashboardDto = facadeService.getUniverseDashboardDto();
        IndexDashboardDto indexDashboardDto = facadeService.getMarketDashboard();
        model.addAttribute("loginMember", loginMember);
        model.addAttribute("universe", universeDashboard);
        model.addAttribute("index", indexDashboardDto);
        model.addAttribute("marketTrendResult", marketDataAnalyzer.analyze(indexDashboardDto, universeDashboardDto));
        return "market/market";
    }
}