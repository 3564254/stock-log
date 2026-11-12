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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class NewsService {

    // TODO: 뉴스 페이징 처리

    private final List<NewsProvider> providers;
    private final NewsRepository newsRepository;


    /** 시장 페이지 뉴스 탭: Finnhub 영어 원문 */
    public NewsResponse getMarketNews(Pageable pageable) {
        // DB에서 페이징된 News 엔티티 조회
        Page<News> newsPage = newsRepository.findMarketNewsByProvider(NewsSource.FINNHUB, pageable);

        // Page<News>를 Page<NewsDto>로 변환
        Page<NewsDto> newsDtoPage = newsPage.map(NewsDto::from);

        // 페이징 정보가 담긴 NewsResponse 생성 후 반환
        return NewsResponse.createMarketNews(newsDtoPage);
    }

    /** 메인 "실시간 핵심 뉴스": KIS 한국어 헤드라인 최신 N건 (종목 기사 포함) */
    public List<NewsDto> getKoreanHeadlines(int n) {
        return newsRepository.findByProviderOrderByDateTimeDesc(NewsSource.KIS, PageRequest.of(0, n)).stream()
                .map(NewsDto::from).toList();
    }

    /** 출처 구분 없이 최신 N건 */
    public List<NewsDto> getMarketNewsDESCTopN(int n) {
        return newsRepository.findNewsDESCTopN(n).stream()
                .map(NewsDto::from).toList();
    }




    // 종목별 마지막 Finnhub 조회 시각
    private static final long STOCK_NEWS_REFRESH_SEC = 30 * 60;
    private final Map<String, Instant> stockNewsFetchedAt = new ConcurrentHashMap<>();

    /** 분석 화면 '회사 소식': Finnhub 종목 뉴스 최근 14일, 최신순, 비슷한 제목은 하나만 */
    public List<NewsDto> getRecentStockNewsFromFinnhub(String symbol, int limit) {
        String code = symbol.toUpperCase(Locale.ROOT);
        Instant last = stockNewsFetchedAt.get(code);
        if (last == null || last.plusSeconds(STOCK_NEWS_REFRESH_SEC).isBefore(Instant.now())) {
            stockNewsFetchedAt.put(code, Instant.now());
            providers.stream()
                    .filter(p -> p.source() == NewsSource.FINNHUB)
                    .findFirst()
                    .ifPresent(p -> saveNewNews(p.fetchStockNews(new StockRequest(code, null)), p.source(), code + " 종목"));
        }
        LocalDateTime after = LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusDays(14);
        List<News> rows = newsRepository.findByRelatedAndProviderAndDateTimeAfterOrderByDateTimeDesc(
                code, NewsSource.FINNHUB, after, PageRequest.of(0, 40));
        Set<String> seen = new HashSet<>();
        List<NewsDto> out = new ArrayList<>();
        for (News n : rows) {
            String h = n.getHeadline() == null ? "" : n.getHeadline();
            String key = h.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (key.isEmpty() || !seen.add(key.substring(0, Math.min(50, key.length())))) continue;
            out.add(NewsDto.from(n));
            if (out.size() >= limit) break;
        }
        return out;
    }

    /** Finnhub 기사들을 주어진 ID 순서대로 */
    public List<NewsDto> getFinnhubNewsByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        Map<String, News> byId = new java.util.HashMap<>();
        for (News n : newsRepository.findByProviderAndNewsIdIn(NewsSource.FINNHUB, ids)) byId.put(n.getNewsId(), n);
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).map(NewsDto::from).toList();
    }

    public NewsResponse fetchStockNews(StockDto stockDto, Pageable pageable) {
        StockRequest request = StockRequest.from(stockDto);

        // API에서 뉴스 리스트 가져오기
        NewsProvider provider = providers.stream()
                .filter(p -> p.supports(stockDto.market()))
                .min(java.util.Comparator.comparingInt(p -> p.source() == NewsSource.FINNHUB ? 0 : 1))
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));
        List<NewsDto> fetchedNewsList = provider.fetchStockNews(request);

        // 새로운 뉴스 DB 저장
        saveNewNews(fetchedNewsList, provider.source(), stockDto.code() + " 종목");

        Page<News> newsPage = newsRepository.findStockNewsByProvider(stockDto.code(), provider.source(), pageable);
        Page<NewsDto> newsDtoPage = newsPage.map(NewsDto::from);

        // 페이징 정보가 담긴 NewsResponse 반환
        return NewsResponse.createStockNews(stockDto, newsDtoPage);
    }


    // 제공처마다 따로 받아 저장한다
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

        // 한 응답 안에 같은 ID가 두 번 오면 저장 중 고유 제약에 걸리므로 먼저 하나만 남긴다
        Map<String, NewsDto> byId = new java.util.LinkedHashMap<>();
        for (NewsDto dto : fetchedNewsList) {
            if (dto.newsId() != null && !dto.newsId().isBlank()) byId.putIfAbsent(dto.newsId(), dto);
        }
        if (byId.isEmpty()) {
            return;
        }

        // 같은 제공처 안에서 이미 저장된 ID (검색 성능 O(1)을 위해 Set)
        Set<String> existingNewsIds = new HashSet<>(newsRepository.findExistingNewsIds(source, new ArrayList<>(byId.keySet())));

        // 기존 DB에 없는 "새로운" 뉴스만 필터링하여 엔티티로 변환
        List<News> newEntitiesToSave = byId.values().stream()
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