package com.stocklog.modules.market.service;

import com.stocklog.global.error.BusinessException;
import com.stocklog.global.error.ErrorCode;
import com.stocklog.modules.market.news.News;
import com.stocklog.modules.market.news.NewsProvider;
import com.stocklog.modules.market.news.NewsRepository;
import com.stocklog.modules.market.news.NewsSource;
import com.stocklog.modules.stock.application.request.StockRequest;
import com.stocklog.modules.market.news.dto.NewsDto;
import com.stocklog.modules.market.news.dto.NewsResponse;
import com.stocklog.modules.stock.stock.dto.StockDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class NewsService {

    // TODO: 뉴스 페이징 처리

    private final List<NewsProvider> providers;
    private final NewsRepository newsRepository;


    /** 시장 페이지 뉴스 탭: Finnhub 영어 원문 (요약·이미지·링크가 있는 기사) */
    public NewsResponse getMarketNews(Pageable pageable) {
        // 1. DB에서 페이징된 News 엔티티 조회
        Page<News> newsPage = newsRepository.findMarketNewsByProvider(NewsSource.FINNHUB, pageable);

        // 2. Page<News>를 Page<NewsDto>로 변환
        Page<NewsDto> newsDtoPage = newsPage.map(NewsDto::from);

        // 3. 페이징 정보가 담긴 NewsResponse 생성 후 반환
        return NewsResponse.createMarketNews(newsDtoPage);
    }

    /** 메인 "실시간 핵심 뉴스": KIS 한국어 헤드라인 최신 N건 (종목 기사 포함) */
    public List<NewsDto> getKoreanHeadlines(int n) {
        return newsRepository.findByProviderOrderByDateTimeDesc(NewsSource.KIS, PageRequest.of(0, n)).stream()
                .map(NewsDto::from).toList();
    }

    /** 출처 구분 없이 최신 N건 — 시장 진단(AI)에 참고 자료로 넘긴다 */
    public List<NewsDto> getMarketNewsDESCTopN(int n) {
        return newsRepository.findNewsDESCTopN(n).stream()
                .map(NewsDto::from).toList();
    }




    public NewsResponse fetchStockNews(StockDto stockDto, Pageable pageable) {
        StockRequest request = StockRequest.from(stockDto);

        // 1. API에서 뉴스 리스트 가져오기
        NewsProvider provider = providers.stream()
                .filter(p -> p.supports(stockDto.market()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));
        List<NewsDto> fetchedNewsList = provider.fetchStockNews(request);

        // 2. 새로운 뉴스 DB 저장
        saveNewNews(fetchedNewsList, provider.source(), stockDto.code() + " 종목");

        Page<News> newsPage = newsRepository.findStockNews(stockDto.code(), pageable);
        Page<NewsDto> newsDtoPage = newsPage.map(NewsDto::from);

        // 4. 페이징 정보가 담긴 NewsResponse 반환
        return NewsResponse.createStockNews(stockDto, newsDtoPage);
    }


    // 제공처마다 따로 받아 저장한다 — 한 곳이 실패해도 나머지는 저장되고, 출처가 섞이지 않는다
    @Scheduled(cron = "0 */3 * * * *")
    public void fetchMarketNews() {
        for (NewsProvider provider : providers) {
            try {
                List<NewsDto> fetched = provider.fetchMarketNews();
                if (fetched == null || fetched.isEmpty()) {
                    log.warn("[News] {} 시장 뉴스 응답 없음", provider.source());
                    continue;
                }
                saveNewNews(fetched, provider.source(), provider.source() + " 시장");
            } catch (Exception e) {
                log.error("[News] {} 시장 뉴스 수집 실패: {}", provider.source(), e.getMessage(), e);
            }
        }
    }

    private void saveNewNews(List<NewsDto> fetchedNewsList, NewsSource source, String logContext) {
        if (fetchedNewsList == null || fetchedNewsList.isEmpty()) {
            return;
        }

        List<String> fetchedNewsIds = fetchedNewsList.stream()
                .map(NewsDto::newsId)
                .toList();

        // DB에 이미 존재하는 news_id 목록 조회 후 Set으로 변환 (검색 성능 O(1) 최적화)
        Set<String> existingNewsIds = new HashSet<>(newsRepository.findExistingNewsIds(fetchedNewsIds));

        // 기존 DB에 없는 "새로운" 뉴스만 필터링하여 엔티티로 변환
        List<News> newEntitiesToSave = fetchedNewsList.stream()
                .filter(dto -> !existingNewsIds.contains(dto.newsId()))
                .map(dto -> NewsDto.toEntity(dto, source))
                .toList();

        // 새로운 뉴스만 저장
        if (!newEntitiesToSave.isEmpty()) {
            newsRepository.saveAll(newEntitiesToSave);
            log.info("[News] {}의 새로운 뉴스 {}건 저장 완료", logContext, newEntitiesToSave.size());
        }
    }

}