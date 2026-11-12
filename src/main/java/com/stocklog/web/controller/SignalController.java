package com.stocklog.web.controller;

import com.stocklog.global.annotation.LoginUser;
import com.stocklog.modules.member.dto.response.MemberResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** 신호 성과 화면: 엔진 신호를 기록해 두고 이후 결과를 비교군과 견준다 */
@Controller
public class SignalController {
    @GetMapping("/signals")
    public String page(@LoginUser MemberResponse loginMember, Model model) {
        if (loginMember == null) return "redirect:/auth/login";
        model.addAttribute("loginMember", loginMember);
        return "stock/signals";
    }
}
