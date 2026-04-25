package com.stocklog.web.controller;

import com.stocklog.domain.marketdata.exchange.service.ExchangeService;
import com.stocklog.domain.marketdata.index.dto.MarketDashboardResponse;
import com.stocklog.domain.marketdata.index.service.IndexService;
import com.stocklog.domain.marketdata.index.type.IndexType;
import com.stocklog.domain.member.dto.response.MemberResponse;
import com.stocklog.global.annotation.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class MarketViewController {

    private final IndexService indexService;
    private final ExchangeService exchangeService;

    @GetMapping("/market")
    public String marketPage(@LoginUser MemberResponse loginMember, Model model) {
        model.addAttribute("loginMember", loginMember);
        model.addAttribute("dashboard", indexService.getMarketDashboard());
        return "market/market";
    }
}