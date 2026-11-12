package com.stocklog.web.api;

import com.stocklog.modules.market.exchange.dto.ExchangeRateInfo;
import com.stocklog.modules.market.index.dto.IndexDto;
import com.stocklog.modules.market.news.dto.NewsDto;
import com.stocklog.modules.market.news.dto.NewsResponse;
import com.stocklog.modules.market.open.MarketOpenService;
import com.stocklog.modules.market.open.MarketOpenResponse;
import com.stocklog.modules.market.service.MarketFacadeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/marketData")
@RequiredArgsConstructor
@Slf4j
public class MarketApiController {

    private final MarketFacadeService marketFacadeService;
    private final MarketOpenService marketOpenService;
    private final com.stocklog.modules.market.weekly.MarketWeeklyService weeklyService;

    @GetMapping("/exchange")
    public ExchangeRateInfo getExchangeRate() {
        return marketFacadeService.fetchExchangeRate();
    }

    @GetMapping("/{seriesId}/index")
    public IndexDto getIndexResponse(@PathVariable String seriesId) {
        return marketFacadeService.getIndexDto(seriesId);
    }

    /** 시장 페이지 뉴스 탭 */
    @GetMapping("/news")
    public NewsResponse getMarketNews(
            @PageableDefault(size = 10, sort = "dateTime", direction = Sort.Direction.DESC) Pageable pageable) {
        int size = Math.min(Math.max(pageable.getPageSize(), 1), 30);
        int page = Math.max(pageable.getPageNumber(), 0);
        return marketFacadeService.fetchMarketNews(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dateTime")));
    }

    /** 메인 "실시간 핵심 뉴스" */
    @GetMapping("/news/headlines")
    public List<NewsDto> getKoreanHeadlines(@RequestParam(defaultValue = "6") int size) {
        return marketFacadeService.fetchKoreanHeadlines(Math.min(Math.max(size, 1), 20));
    }

    /** 시장 화면 '이번 주 대응 · 어디를 볼지' 재료. 30분 캐시 */
    @GetMapping("/weekly")
    public com.stocklog.modules.market.weekly.MarketWeeklyService.Weekly getWeekly() {
        return weeklyService.weekly();
    }

    /** 국내·미국 장 운영 상태 */
    @GetMapping("/calendar")
    public MarketOpenResponse getMarketOpenResponse() {
        return marketOpenService.getMarketOpenResponse();
    }
}
