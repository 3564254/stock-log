package com.stocklog.web.controller;

import com.stocklog.modules.market.analysis.dto.MarketRegimeResponse;
import com.stocklog.modules.market.index.dto.IndexDashboardDto;
import com.stocklog.modules.market.news.dto.NewsResponse;
import com.stocklog.modules.market.service.MarketFacadeService;
import com.stocklog.modules.member.dto.response.MemberResponse;
import com.stocklog.global.annotation.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.function.Supplier;

@Slf4j
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
        // 지표·진단·뉴스는 서로 다른 외부 API에 기대므로, 한 곳이 실패해도 나머지는 보여 준다
        IndexDashboardDto indexDashboard = loadSection("지표 대시보드", marketFacadeService::getMarketDashboard);
        MarketRegimeResponse marketAnalysisResult = loadSection("시장 진단", () -> marketFacadeService.getMarketAnalysisResponse(15));
        NewsResponse news = loadSection("시장 뉴스", () -> marketFacadeService.fetchMarketNews(pageable));

        model.addAttribute("loginMember", loginMember);
        model.addAttribute("indexDashboard", indexDashboard);
        model.addAttribute("marketAnalysis", marketAnalysisResult);
        model.addAttribute("news", news);
        model.addAttribute("indexError", indexDashboard == null);
        model.addAttribute("analysisError", marketAnalysisResult == null);
        model.addAttribute("newsError", news == null);

        return "market/market";
    }

    private <T> T loadSection(String name, Supplier<T> loader) {
        try {
            return loader.get();
        } catch (Exception e) {
            log.error("[시장 화면] {} 로딩 실패", name, e);
            return null;
        }
    }
}
