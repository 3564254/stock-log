package com.stocklog.web.api;

import com.stocklog.application.dashboard.MarketStripResponse;
import com.stocklog.application.dashboard.service.MarketStripService;
import com.stocklog.modules.market.ranking.MarketRankingInfo;
import com.stocklog.modules.market.ranking.MarketRankingService;
import com.stocklog.modules.stock.screening.ScreeningService;
import com.stocklog.modules.stock.screening.dto.ScreeningResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 메인 화면 위젯용 API */
@RestController
@RequestMapping("/api/main")
@RequiredArgsConstructor
public class MainApiController {

    private final MarketRankingService rankingService;
    private final ScreeningService screeningService;
    private final MarketStripService marketStripService;

    /** 주도주 및 거래대금: 지금 열린 시장(국내 우선)의 거래대금 상위 */
    @GetMapping("/ranking")
    public MarketRankingInfo getMarketRanking(@RequestParam(defaultValue = "5") int size) {
        return rankingService.getMarketRankingInfo(size);
    }

    /** 진입 후보: 장 마감 뒤 분석해 둔 시가총액 상위 종목 중 진입 판정이 나온 종목 (DB만 읽는다) */
    @GetMapping("/screening")
    public ScreeningResponse getScreening(@RequestParam(defaultValue = "6") int size) {
        return screeningService.getCandidates(Math.min(Math.max(size, 1), 100));   // 홈 카드 5개, 더 보기 창 100개
    }

    /** 상단 지수 띠: 시장 국면 → 미국 지수 → 국내 지수 → 환율 (60초 캐시) */
    @GetMapping("/strip")
    public MarketStripResponse getMarketStrip() {
        return marketStripService.getStrip();
    }
}
