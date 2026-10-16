package com.stocklog.web.controller;

import com.stocklog.modules.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 커뮤니티 화면. 화면 뼈대만 내려주고 글·댓글은 /api/community 로 불러온다.
 * (더 보기·좋아요·댓글 때 페이지 전체를 다시 그리지 않으려고)
 * loginMember 는 LoginMemberAdvice 가 모델에 넣어 준다.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/community")
public class CommunityController {

    private final PostService postService;

    /** /community, /community?stock=005930 (종목별 글) */
    @GetMapping
    public String feed(@RequestParam(required = false) String stock, Model model) {
        model.addAttribute("stockFilter", postService.findStockTag(stock).orElse(null));
        return "community/community";
    }

    @GetMapping("/posts/{postId}")
    public String detail(@PathVariable Long postId, Model model) {
        model.addAttribute("postId", postId);
        return "community/detail";
    }
}
