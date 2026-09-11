package com.stocklog.web.controller;

import com.stocklog.modules.member.dto.response.MemberResponse;
import com.stocklog.modules.purchase.trade.dto.request.TradeLogRequest;
import com.stocklog.modules.purchase.trade.dto.response.TradeLogResponse;
import com.stocklog.modules.purchase.trade.entity.SortType;
import com.stocklog.modules.purchase.trade.service.TradeFacadeService;
import com.stocklog.global.annotation.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/trade")
public class TradingController {

    private final TradeFacadeService tradeService;

    @GetMapping()
    public String tradeLogPage(
            @LoginUser MemberResponse loginMember, Model model
    ) {
        if (loginMember == null) return "redirect:/";

        model.addAttribute("loginMember", loginMember);
        TradeLogResponse monthlyTradeLogs = tradeService.getMonthlyTradeLogs(loginMember.getId(), LocalDate.now(), SortType.DATE_ASC);
        model.addAttribute("tradelog", monthlyTradeLogs);
        return "portfolio/tradelog";
    }

    @PostMapping("/add")
    public String saveTradeLog(
            @LoginUser MemberResponse loginMember,
            @ModelAttribute TradeLogRequest request
    ) {
        tradeService.saveTradeLog(loginMember.getId(), request);
        return "redirect:/trade";
    }

    @GetMapping("/api")
    @ResponseBody
    public ResponseEntity<TradeLogResponse> getTradeLogApi(
            @LoginUser MemberResponse loginMember,
            @RequestParam String date
    ) {
        LocalDate targetDate = LocalDate.parse(date);
        TradeLogResponse response = tradeService.getMonthlyTradeLogs(
                loginMember.getId(), targetDate, SortType.DATE_ASC);
        return ResponseEntity.ok(response);
    }
}