package com.stocklog.web.controller;

import com.stocklog.modules.market.analysis.dto.MarketRegimeDto;
import com.stocklog.modules.market.analysis.dto.MarketRegimeResponse;
import com.stocklog.modules.market.index.dto.IndexDashboardDto;
import com.stocklog.modules.market.news.dto.NewsResponse;
import com.stocklog.modules.market.service.MarketFacadeService;
import com.stocklog.modules.member.dto.response.MemberResponse;
import com.stocklog.global.annotation.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class MarketController {

    private final MarketFacadeService marketFacadeService;




    @GetMapping("/market")
    public String marketPage(
            @LoginUser MemberResponse loginMember,
            @PageableDefault(size = 10, sort = "dateTime", direction = Sort.Direction.DESC) Pageable pageable,
            Model model
    ) {

        IndexDashboardDto indexDashboard = marketFacadeService.getMarketDashboard();
        MarketRegimeResponse marketAnalysisResult = marketFacadeService.getMarketAnalysisResponse(15);
        NewsResponse news = marketFacadeService.fetchMarketNews(pageable);


        model.addAttribute("loginMember", loginMember);
        model.addAttribute("indexDashboard", indexDashboard);
        model.addAttribute("marketAnalysis", marketAnalysisResult);
        model.addAttribute("news", news);

        return "market/market";
    }
}