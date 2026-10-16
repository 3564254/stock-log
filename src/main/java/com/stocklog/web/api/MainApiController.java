package com.stocklog.web.api;

import com.stocklog.modules.market.ranking.MarketRankingInfo;
import com.stocklog.modules.market.ranking.MarketRankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 메인 화면 위젯용 API — 페이지를 먼저 띄우고 외부 API가 필요한 위젯은 따로 불러온다
 */
@RestController
@RequestMapping("/api/main")
@RequiredArgsConstructor
public class MainApiController {

    private final MarketRankingService rankingService;

    /** 주도주 및 거래대금: 지금 열린 시장(국내 우선)의 거래대금 상위 */
    @GetMapping("/ranking")
    public MarketRankingInfo getMarketRanking(@RequestParam(defaultValue = "5") int size) {
        return rankingService.getMarketRankingInfo(size);
    }
}
