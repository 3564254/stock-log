package com.stocklog.web.advice;

import com.stocklog.global.annotation.LoginUser;
import com.stocklog.modules.member.dto.response.MemberResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 모든 화면(@Controller)에 로그인 정보를 넣어 준다.
 * 상단 내비게이션(fragments/sidebar)이 ${loginMember}로 메뉴·프로필을 그리는데,
 * 커뮤니티·내 정보·오류 화면처럼 컨트롤러가 직접 넣지 않는 화면에서도 같은 내비게이션이 보이게 하려는 것.
 * 컨트롤러가 model.addAttribute("loginMember", ...)로 넣으면 그 값이 그대로 쓰인다.
 */
@ControllerAdvice(annotations = Controller.class)
public class LoginMemberAdvice {

    @ModelAttribute("loginMember")
    public MemberResponse loginMember(@LoginUser MemberResponse loginMember) {
        return loginMember; // 비로그인이면 null
    }
}
