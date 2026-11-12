package com.stocklog.web.advice;

import com.stocklog.global.annotation.LoginUser;
import com.stocklog.modules.member.dto.response.MemberResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 모든 화면(@Controller)에 로그인 정보를 넣어 준다 */
@ControllerAdvice(annotations = Controller.class)
public class LoginMemberAdvice {

    @ModelAttribute("loginMember")
    public MemberResponse loginMember(@LoginUser MemberResponse loginMember) {
        return loginMember; // 비로그인이면 null
    }
}
