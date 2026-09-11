package com.stocklog.web.controller;


import com.stocklog.modules.member.dto.response.FriendResponse;
import com.stocklog.modules.member.dto.response.MemberResponse;
import com.stocklog.modules.member.dto.response.PendingResponse;
import com.stocklog.modules.member.service.MemberFacadeService;
import com.stocklog.modules.purchase.portfolio.dto.response.PortfolioResponse;
import com.stocklog.modules.purchase.portfolio.service.PortfolioFacadeService;
import com.stocklog.global.annotation.LoginUser;
import com.stocklog.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/friends")
public class FriendController {

    private final MemberFacadeService memberService;
    private final PortfolioFacadeService portfolioService;


    @GetMapping()
    public String friendsPage(@LoginUser MemberResponse loginMember, Model model) {
        if (loginMember == null) return "redirect:/auth/login";
        List<FriendResponse> myFriends = memberService.findMyFriends(loginMember.getId());
        List<PendingResponse> pendingRequests = memberService.findPendingRequest(loginMember.getId());

        model.addAttribute("me", loginMember);
        model.addAttribute("loginMember", loginMember);
        model.addAttribute("pendingRequests", pendingRequests);
        model.addAttribute("friends", myFriends);
        model.addAttribute("friendCount", myFriends.size());

        return "friends/friends";
    }

    @GetMapping("/search")
    @ResponseBody
    public ResponseEntity<?> findMember(
                                         @LoginUser MemberResponse loginMember,
                                         @RequestParam String loginId
    ) {
        try {
            MemberResponse member = memberService.getMemberResponseByLoginId(loginId);
            return ResponseEntity.ok(member);
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("에러 발생: " + e.getMessage());
        }
    }


    @PostMapping("/add")
    @ResponseBody
    public ResponseEntity<?> addFriend(@RequestParam String keyword,
                                       @LoginUser MemberResponse loginMember
    ) {
        try {
            MemberResponse targetMember = memberService.getMemberResponseByLoginId(keyword);

            if (targetMember == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("해당 사용자를 찾을 수 없습니다.");
            }

            memberService.sendRequest(loginMember.getId(), targetMember.getId());

            return ResponseEntity.ok().build();

        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("요청 중 오류가 발생했습니다.");
        }
    }


    @PostMapping("/accept")
    public String acceptFriend(@RequestParam Long friendshipId, @LoginUser MemberResponse loginMember) {
        memberService.acceptFriend(friendshipId, loginMember.getId());
        return "redirect:/friends";
    }

    @PostMapping("/reject")
    public String rejectFriend(@RequestParam Long friendshipId, @LoginUser MemberResponse loginMember) {
        memberService.removeFriend(friendshipId);
        return "redirect:/friends";
    }

    @GetMapping("/{friendId}")
    public String showFriendPortfolio(@PathVariable Long friendId,
                                      @LoginUser MemberResponse loginMember,
                                      Model model
    ) {
        if (loginMember == null) return "redirect:/auth/login";

        if (!memberService.isFriend(loginMember.getId(), friendId)) return "redirect:/friends?error=not-friend";
        MemberResponse friendInfo = memberService.getInfo(friendId);
        PortfolioResponse friendPortfolio = portfolioService.getMyPortfolio(friendId);

        model.addAttribute("loginMember", loginMember);
        model.addAttribute("friend", friendInfo);
        model.addAttribute("portfolio", friendPortfolio);

        return "friends/friend_portfolio";
    }


}