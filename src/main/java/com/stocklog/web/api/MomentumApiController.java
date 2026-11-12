package com.stocklog.web.api;

import com.stocklog.global.annotation.LoginUser;
import com.stocklog.global.error.BusinessException;
import com.stocklog.global.error.ErrorCode;
import com.stocklog.modules.member.dto.response.MemberResponse;
import com.stocklog.modules.stock.momentum.MomentumScheduler;
import com.stocklog.modules.stock.momentum.MomentumService;
import com.stocklog.modules.stock.momentum.RelativeStrengthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 미국 모멘텀 순위 API */
@RestController
@RequestMapping("/api/momentum")
@RequiredArgsConstructor
public class MomentumApiController {

    private final MomentumService service;
    private final MomentumScheduler scheduler;
    private final RelativeStrengthService rsService;
    private final com.stocklog.modules.stock.momentum.SectorTrendService sectorTrendService;

    /** 상위 20% (진입 후보) 목록 */
    @GetMapping("/top")
    public List<MomentumService.MomentumView> top() { return service.top(); }

    @GetMapping("/status")
    public MomentumService.Status status() { return service.status(); }

    /** 순위 다시 계산 (백그라운드, 몇 분) */
    @PostMapping("/refresh")
    public MomentumService.Status refresh(@LoginUser MemberResponse loginMember) {
        if (loginMember == null) throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        scheduler.submit();
        return service.status();
    }

    /** 6개월 상대 강도 */
    /** 업종 추세 */
    @GetMapping("/{code}/sector")
    public com.stocklog.modules.stock.momentum.SectorTrendService.SectorTrend sector(@PathVariable String code) {
        return sectorTrendService.view(code);
    }

    @GetMapping("/{code}/rs")
    public RelativeStrengthService.RsView rs(@PathVariable String code) { return rsService.view(code); }

    /** 종목 하나의 순위 */
    @GetMapping("/{code}")
    public MomentumService.MomentumView one(@PathVariable String code) { return service.view(code); }
}
