package com.stocklog.web.controller;

import com.stocklog.global.annotation.LoginUser;
import com.stocklog.modules.member.dto.response.MemberResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** 미국 모멘텀 상위 20% 화면 */
@Controller
public class MomentumController {
    @GetMapping("/momentum")
    public String page(@LoginUser MemberResponse loginMember, Model model) {
        if (loginMember == null) return "redirect:/auth/login";
        model.addAttribute("loginMember", loginMember);
        return "stock/momentum";
    }
}
