package com.stocklog.web.controller;

import com.stocklog.modules.purchase.application.PortfolioDashboardResponse;
import com.stocklog.modules.member.dto.response.MemberResponse;
import com.stocklog.modules.purchase.application.service.PortfolioDashboardService;
import com.stocklog.global.annotation.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/portfolio")
public class PortfolioController {

    private final PortfolioDashboardService portfolioDashboardService;

    @GetMapping()
    public String portfolioPage(@LoginUser MemberResponse loginMember, Model model) {
        if (loginMember == null) return "redirect:/auth/login";

        model.addAttribute("loginMember", loginMember);

        // 서비스 계층에서 필요한 모든 초기 데이터를 긁어옴 (가벼운 조회)
        PortfolioDashboardResponse dashboardData = portfolioDashboardService.getDashboardData(loginMember.getId());

        model.addAttribute("portfolio", dashboardData.portfolio());

        // 타임리프가 객체 리스트를 자동으로 자바스크립트 배열로 직렬화하도록 그대로 넘김
        model.addAttribute("watchList", dashboardData.watchList().itemResponseList());
        model.addAttribute("rulesList", dashboardData.tradingRules().ruleList());

        return "portfolio/portfolio";
    }
}