package com.stocklog.web.controller;

import com.stocklog.modules.member.dto.request.*;
import com.stocklog.modules.member.dto.response.MemberDetailResponse;
import com.stocklog.modules.member.dto.response.MemberResponse;
import com.stocklog.modules.member.service.MemberFacadeService;
import com.stocklog.global.annotation.LoginUser;
import com.stocklog.modules.member.entity.session.SessionConst;
import com.stocklog.global.error.BusinessException;
import com.stocklog.global.error.ErrorCode;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {
    private final MemberFacadeService memberService;

    @GetMapping("/login")
    public String loginForm(Model model) {
        model.addAttribute("loginRequest", new LoginRequest());
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@Valid LoginRequest loginRequest,
                        BindingResult bindingResult,
                        @RequestParam(defaultValue = "/") String redirectURL,
                        HttpSession session,
                        Model model
    ) {
        if (bindingResult.hasErrors()) {
            return "auth/login";
        }
        try {
            MemberResponse member = memberService.login(loginRequest);
            session.setAttribute(SessionConst.LOGIN_MEMBER, member);
            return "redirect:" + redirectURL;
        } catch (BusinessException e) {
            bindingResult.reject("loginFail", e.getMessage());
            return "auth/login";
        }
    }

    @GetMapping("/signup")
    public String signUpForm(Model model) {
        model.addAttribute("signupRequest", new SignupRequest());
        return "auth/signup";
    }

    @PostMapping("/signup")
    public String sign(@Valid SignupRequest signupRequest,
                       BindingResult bindingResult,
                       Model model
    ) {
        if (bindingResult.hasErrors()) {
            return "auth/signup";
        }
        try {
            memberService.signUp(signupRequest);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.DUPLICATE_LOGIN_ID || e.getErrorCode() == ErrorCode.DUPLICATE_EMAIL) {
                bindingResult.reject("signupFail", e.getMessage());
            } else {
                bindingResult.rejectValue("passwordConfirm", "passwordMismatch", e.getMessage());
            }
            return "auth/signup";
        }
        return "redirect:/auth/login";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        if (session != null) session.invalidate();
        return "redirect:/";
    }

    @GetMapping("/info")
    public String infoForm(Model model, @LoginUser MemberResponse loginMember) {
        MemberDetailResponse member = memberService.getDetailInfo(loginMember.getId());
        model.addAttribute("member", member);
        model.addAttribute("usernameRequest", new UsernameRequest());
        model.addAttribute("passwordRequest", new PasswordRequest());
        return "auth/info";
    }

    @PostMapping("/info/update/username")
    public String updateUsername(@Valid UsernameRequest usernameRequest,
                                 BindingResult bindingResult,
                                 @LoginUser MemberResponse loginMember,
                                 Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("member", memberService.getDetailInfo(loginMember.getId()));
            model.addAttribute("openUsernameModal", true);
            model.addAttribute("passwordRequest", new PasswordRequest());
            return "auth/info";
        }
        try {
            memberService.updateUsername(loginMember.getId(), usernameRequest.getUsername());
        } catch (BusinessException e) {
            bindingResult.rejectValue("username", "duplicateUsername", e.getMessage());
            model.addAttribute("member", memberService.getDetailInfo(loginMember.getId()));
            model.addAttribute("openUsernameModal", true);
            model.addAttribute("passwordRequest", new PasswordRequest());
            return "auth/info";
        }
        return "redirect:/auth/info";
    }

    @PostMapping("/info/update/password")
    public String updatePassword(@Valid PasswordRequest passwordRequest,
                                 BindingResult bindingResult,
                                 @LoginUser MemberResponse loginMember,
                                 Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("member", memberService.getDetailInfo(loginMember.getId()));
            model.addAttribute("openPasswordModal", true);
            model.addAttribute("usernameRequest", new UsernameRequest());
            return "auth/info";
        }
        try {
            memberService.updatePassword(loginMember.getId(), passwordRequest);
        } catch (BusinessException e) {
            bindingResult.rejectValue("oldPassword", "invalidPassword", e.getMessage());
            model.addAttribute("member", memberService.getDetailInfo(loginMember.getId()));
            model.addAttribute("openPasswordModal", true);
            model.addAttribute("usernameRequest", new UsernameRequest());
            return "auth/info";
        }
        return "redirect:/auth/info";
    }

    @PostMapping("/info/withdraw")
    public String deleteUser(@LoginUser MemberResponse loginMember,
                             HttpSession session) {
        memberService.deleteById(loginMember.getId());
        if (session != null) session.invalidate();
        return "redirect:/";
    }
}
