package com.stocklog.web.controller;

import com.stocklog.domain.member.dto.request.*;
import com.stocklog.domain.member.dto.response.MemberDetailResponse;
import com.stocklog.domain.member.dto.response.MemberResponse;
import com.stocklog.global.annotation.LoginUser;
import com.stocklog.domain.member.entity.session.SessionConst;
import com.stocklog.domain.member.service.AuthService;
import com.stocklog.domain.member.service.MemberService;
import com.stocklog.global.error.member.InvalidPasswordException;
import com.stocklog.global.error.member.LoginFailureException;
import com.stocklog.global.error.member.SignUpFailureException;
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
    private final AuthService authService;
    private final MemberService memberService;

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
            MemberResponse member = authService.login(loginRequest);
            session.setAttribute(SessionConst.LOGIN_MEMBER, member);
            return "redirect:" + redirectURL;
        } catch (LoginFailureException e) {
            model.addAttribute("errorMessage", e.getMessage());
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
            authService.signUp(signupRequest);
        } catch (InvalidPasswordException e) {
            bindingResult.rejectValue("passwordConfirm", "passwordMismatch", e.getMessage());
            return "auth/signup";
        } catch (SignUpFailureException e) {
            model.addAttribute("errorMessage", e.getMessage());
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
        memberService.updateUsername(loginMember.getId(), usernameRequest.getUsername());
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
        }
        try {
            authService.updatePassword(loginMember.getId(), passwordRequest);
        } catch (InvalidPasswordException e) {
            model.addAttribute("errorMessage", e.getMessage());
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
        if (session != null) session.invalidate();
        memberService.deleteById(loginMember.getId());
        return "redirect:/";
    }
}
