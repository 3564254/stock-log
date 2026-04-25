package com.stocklog.web.controller;

import com.stocklog.domain.member.dto.response.MemberResponse;
import com.stocklog.domain.tradelog.dto.request.TradeLogRequest;
import com.stocklog.domain.tradelog.dto.response.TradeLogResponse;
import com.stocklog.domain.tradelog.service.TradeLogService;
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
public class TradeLogController {
    private final TradeLogService tradeLogService;

    @GetMapping()
    public String tradeLogPage(
            @LoginUser MemberResponse loginMember, Model model
    ) {
        if (loginMember == null) return "redirect:/";

        model.addAttribute("loginMember", loginMember);
        TradeLogResponse monthlyTradeLogs = tradeLogService.getMonthlyTradeLogs(loginMember.getId(), LocalDate.now(), TradeLogService.SortType.DATE_ASC);
        model.addAttribute("tradelog", monthlyTradeLogs);
        return "portfolio/tradelog";
    }


    @PostMapping("/add")
    public String saveTradeLog(
            @LoginUser MemberResponse loginMember,
            TradeLogRequest request
    ) {
        tradeLogService.saveTradeLog(loginMember.getId(), request);
        return "redirect:/trade";
    }


    @GetMapping("/api")
    @ResponseBody
    public ResponseEntity<TradeLogResponse> getTradeLogApi(
            @LoginUser MemberResponse loginMember,
            @RequestParam String date
    ) {
        LocalDate targetDate = LocalDate.parse(date);
        TradeLogResponse response = tradeLogService.getMonthlyTradeLogs(
                loginMember.getId(), targetDate, TradeLogService.SortType.DATE_ASC);
        log.info("TradeLog API: {}", response);
        return ResponseEntity.ok(response);
    }
}
