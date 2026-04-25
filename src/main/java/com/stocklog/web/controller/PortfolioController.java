package com.stocklog.web.controller;

import com.stocklog.domain.member.dto.response.MemberResponse;
import com.stocklog.domain.portfolio.dto.response.PortfolioAnalysisResponse;
import com.stocklog.domain.portfolio.dto.response.PortfolioResponse;
import com.stocklog.domain.portfolio.service.PortfolioAnalysisService;
import com.stocklog.global.annotation.LoginUser;
import com.stocklog.domain.portfolio.dto.request.AddPortfolioStockRequest;
import com.stocklog.domain.portfolio.dto.request.HoldingUpdateRequest;
import com.stocklog.domain.portfolio.service.PortfolioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/portfolio")
public class PortfolioController {
    private final PortfolioService portfolioService;
    private final PortfolioAnalysisService portfolioAnalysisService;

    @GetMapping()
    public String portfolioPage(@LoginUser MemberResponse loginMember, Model model) {
        if (loginMember == null) return "redirect:/auth/login";

        PortfolioResponse portfolio = portfolioService.getMyPortfolio(loginMember.getId());
        model.addAttribute("portfolio", portfolio);
        model.addAttribute("loginMember", loginMember);
        model.addAttribute("analysis", portfolioAnalysisService.analyzePortfolio(portfolio));
        return "portfolio/portfolio";
    }

    @PostMapping("/add")
    public String addStock(@LoginUser MemberResponse loginMember, @Valid AddPortfolioStockRequest request) {
        if (loginMember == null) return "redirect:/auth/login";
        portfolioService.updateHolding(loginMember.getId(), HoldingUpdateRequest.stockToHoldings(request));
        return "redirect:/";
    }

    @PostMapping("/update")
    public String updateStock(@LoginUser MemberResponse loginMember, @ModelAttribute HoldingUpdateRequest request) {
        if (loginMember == null) return "redirect:/auth/login";
        portfolioService.updateHolding(loginMember.getId(), request); //
        return "redirect:/";
    }

    @PostMapping("/delete")
    public String deleteStock(@LoginUser MemberResponse loginMember, @RequestParam String code) {
        if (loginMember == null) return "redirect:/auth/login";
        portfolioService.deleteHolding(loginMember.getId(), code); //
        return "redirect:/";
    }

}
