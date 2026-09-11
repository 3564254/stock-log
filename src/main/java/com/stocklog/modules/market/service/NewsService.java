package com.stocklog.modules.market.service;

import com.stocklog.modules.market.news.News;
import com.stocklog.modules.market.news.NewsProvider;
import com.stocklog.modules.market.news.NewsRepository;
import com.stocklog.modules.stock.application.request.StockRequest;
import com.stocklog.modules.market.news.dto.NewsDto;
import com.stocklog.modules.market.news.dto.NewsResponse;
import com.stocklog.modules.stock.stock.dto.StockDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
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


    public NewsResponse getMarketNews(Pageable pageable) {
        // 1. DB에서 페이징된 News 엔티티 조회
        Page<News> newsPage = newsRepository.findMarketNews(pageable);

        // 2. Page<News>를 Page<NewsDto>로 변환
        Page<NewsDto> newsDtoPage = newsPage.map(NewsDto::from);

        // 3. 페이징 정보가 담긴 NewsResponse 생성 후 반환
        return NewsResponse.createMarketNews(newsDtoPage);
    }



    public NewsResponse fetchStockNews(StockDto stockDto, Pageable pageable) {
        StockRequest request = StockRequest.from(stockDto);

        // 1. API에서 뉴스 리스트 가져오기
        List<NewsDto> fetchedNewsList = providers.stream()
                .filter(p -> p.supports(stockDto.market()))
                .findFirst()
                .map(p -> p.fetchStockNews(request))
                .orElseThrow(() -> {
                    log.error("[News] 지원하지 않는 시장입니다: {}", stockDto.market());
                    return new IllegalArgumentException("지원하지 않는 시장입니다.");
                });

        // 2. 새로운 뉴스 DB 저장
        saveNewNews(fetchedNewsList, stockDto.code() + " 종목");

        Page<News> newsPage = newsRepository.findStockNews(stockDto.code(), pageable);
        Page<NewsDto> newsDtoPage = newsPage.map(NewsDto::from);

        // 4. 페이징 정보가 담긴 NewsResponse 반환
        return NewsResponse.createStockNews(stockDto, newsDtoPage);
    }


    //@Scheduled(cron = "0 0 0 * * *") // 매분 0초마다 실행 (1분 간격)
    @Scheduled(cron = "0 * * * * *")
    public void fetchMarketNews() {
        // 1. API에서 시장 뉴스 리스트 가져오기
        List<NewsDto> fetchedNewsList = providers.stream()
                .findFirst()
                .map(NewsProvider::fetchMarketNews)
                .orElseThrow(() -> {
                    log.error("[News] 새로운 시장 뉴스가 없습니다.");
                    return new IllegalArgumentException("[News] 새로운 시장 뉴스가 없습니다.");
                });

        // 2. 새로운 뉴스 DB 저장
        saveNewNews(fetchedNewsList, "시장");
    }

    private void saveNewNews(List<NewsDto> fetchedNewsList, String logContext) {
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
                .map(NewsDto::toEntity)
                .toList();

        // 새로운 뉴스만 저장
        if (!newEntitiesToSave.isEmpty()) {
            newsRepository.saveAll(newEntitiesToSave);
            log.info("[News] {}의 새로운 뉴스 {}건 저장 완료", logContext, newEntitiesToSave.size());
        } else {
            log.info("[News] {}의 새로운 뉴스가 없습니다 (모두 이미 저장됨)", logContext);
        }
    }

}