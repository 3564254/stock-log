package com.stocklog.web.controller;

import com.stocklog.application.dashboard.service.MainDashboardService;
import com.stocklog.modules.market.open.MarketCalendarService;
import com.stocklog.modules.market.service.ExchangeService;
import com.stocklog.modules.member.dto.response.MemberResponse;
import com.stocklog.global.annotation.LoginUser;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class MainController {

    private final MainDashboardService mainService;
    private final ExchangeService exchangeService;
    private final MarketCalendarService calendarService;

    @GetMapping({"/", "/main"})
    public String mainPage(@LoginUser MemberResponse loginMember, Model model, HttpServletResponse response) {
        setNoCacheHeader(response);

        model.addAttribute("loginMember", loginMember);

        Long memberId = (loginMember != null) ? loginMember.getId() : null;
        model.addAttribute("dashboard", mainService.getDashboardData(memberId));
        model.addAttribute("exchange", exchangeService.fetchExchangeRate());
        model.addAttribute("marketCalendar", calendarService.getMarketCalendarResponse());

        return "main/main";
    }

    private void setNoCacheHeader(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
    }
}
