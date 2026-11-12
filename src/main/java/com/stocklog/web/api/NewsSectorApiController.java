package com.stocklog.web.api;

import com.stocklog.global.annotation.LoginUser;
import com.stocklog.modules.ai.AiException;
import com.stocklog.modules.market.newssector.NewsSectorService;
import com.stocklog.modules.member.dto.response.MemberResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 시장 화면 '오늘 뉴스 → 섹터 영향' (표시만). 로그인하지 않아도 보이고, 로그인하면 내 보유 종목 해당 여부가 붙는다 */
@RestController
@RequestMapping("/api/marketData/news-sectors")
@RequiredArgsConstructor
public class NewsSectorApiController {

    private final NewsSectorService service;

    /** 가장 최근 것 (오늘 것이 아니어도). 없으면 204 */
    @GetMapping
    public ResponseEntity<?> latest(@LoginUser MemberResponse member) {
        return service.latest(member != null ? member.getId() : null)
                .<ResponseEntity<?>>map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    /** 오늘 것 받기 */
    @PostMapping
    public ResponseEntity<?> generate(@LoginUser MemberResponse member) {
        try {
            return ResponseEntity.ok(service.generate(member != null ? member.getId() : null));
        } catch (AiException e) {
            return ResponseEntity.status(e.isQuota() ? 429 : 502).body(Map.of("code", e.code(), "message", e.userMessage()));
        }
    }
}
