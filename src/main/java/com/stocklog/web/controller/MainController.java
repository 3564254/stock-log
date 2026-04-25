package com.stocklog.web.controller;

import com.stocklog.domain.marketdata.exchange.service.ExchangeService;
import com.stocklog.domain.member.dto.response.MemberResponse;
import com.stocklog.domain.portfolio.dto.response.PortfolioResponse;
import com.stocklog.global.annotation.LoginUser;
import com.stocklog.domain.portfolio.service.PortfolioService;
import com.stocklog.domain.portfolio.dto.request.AddPortfolioStockRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;


@Controller
@RequiredArgsConstructor
public class MainController {

    private final PortfolioService portfolioService;
    private final ExchangeService exchangeService;

    @GetMapping({"/", "/main"})
    public String mainPage(@LoginUser MemberResponse loginMember,
                           Model model,
                           HttpServletResponse response) { // HttpServletResponse 추가

        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate"); // HTTP 1.1
        response.setHeader("Pragma", "no-cache"); // HTTP 1.0
        response.setHeader("Expires", "0"); // Proxies

        model.addAttribute("loginMember", loginMember);
        model.addAttribute("portfolio", (loginMember != null) ? portfolioService.getMyPortfolio(loginMember.getId()) : PortfolioResponse.createFallback());
        model.addAttribute("addStockRequest", AddPortfolioStockRequest.createFallback());
        model.addAttribute("exchangeRate", exchangeService.fetchExchangeRate());
        return "main/main";
    }
}
