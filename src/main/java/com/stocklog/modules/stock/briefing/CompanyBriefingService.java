package com.stocklog.modules.stock.briefing;

import com.stocklog.modules.ai.AiException;
import com.stocklog.modules.ai.AiProvider;
import com.stocklog.modules.market.news.dto.NewsDto;
import com.stocklog.modules.market.service.NewsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 분석 화면 '회사 소식': 다음 실적일 · 최근 실적 · 최근 뉴스 제목 · (누르면) AI 뉴스 요약 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CompanyBriefingService {

    private static final Duration EARNINGS_TTL = Duration.ofHours(6);
    private static final int NEWS_LIMIT = 6;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final CompanyEventProvider eventProvider;
    private final NewsService newsService;
    private final AiProvider aiProvider;
    private final NewsSummaryRepository summaryRepository;

    private record Cached(CompanyBriefing.Earnings next, List<CompanyBriefing.PastEarnings> past,
                          List<CompanyBriefing.Analysts> analysts, Instant at) {}
    private final Map<String, Cached> earningsCache = new ConcurrentHashMap<>();
    /** 같은 종목 요약을 동시에 두 번 만들지 않도록 종목별 잠금 */
    private final Map<String, Object> summaryLocks = new ConcurrentHashMap<>();

    /** AI 요약을 만들 수 없을 때 */
    public static class SummaryUnavailableException extends RuntimeException {
        private final String code;
        public SummaryUnavailableException(String code, String message) { super(message); this.code = code; }
        public String code() { return code; }
    }

    public CompanyBriefing get(String code) {
        if (!supports(code)) {
            return CompanyBriefing.unsupported();   // 국내 종목은 아직 지원하지 않는다
        }
        String symbol = code.toUpperCase();
        Cached c = earnings(symbol);
        List<NewsDto> news;
        try {
            news = newsService.getRecentStockNewsFromFinnhub(symbol, NEWS_LIMIT);
        } catch (Exception e) {
            log.warn("[Briefing] 종목 뉴스 조회 실패 {}: {}", symbol, e.getMessage());
            news = List.of();
        }
        // 오늘 요약이 없으면 가장 최근 요약을 보여 준다
        CompanyBriefing.AiSummary ai = summaryRepository.findTopByCodeOrderBySummaryDateDesc(symbol)
                .map(this::toDto).orElse(null);
        return new CompanyBriefing(true, c.next(), c.past(), news, ai, c.analysts());
    }

    /** 다음 실적 발표 (6시간 캐시). AI 리포트 맥락용, 없으면 null */
    public CompanyBriefing.Earnings nextEarnings(String code) {
        return supports(code) ? earnings(code.toUpperCase()).next() : null;
    }

    /** 가장 최근 AI 뉴스 요약 (오늘 아니어도). AI 리포트 맥락용 */
    public java.util.Optional<NewsSummary> latestSummary(String code) {
        return supports(code) ? summaryRepository.findTopByCodeOrderBySummaryDateDesc(code.toUpperCase()) : java.util.Optional.empty();
    }

    private Cached earnings(String symbol) {
        Cached c = earningsCache.get(symbol);
        if (c == null || c.at().plus(EARNINGS_TTL).isBefore(Instant.now())) {
            List<CompanyBriefing.Earnings> up = eventProvider.upcomingEarnings(symbol, 120);
            List<CompanyBriefing.PastEarnings> past = eventProvider.recentEarnings(symbol);
            List<CompanyBriefing.Analysts> rec = eventProvider.recommendations(symbol);
            c = new Cached(up.isEmpty() ? null : up.get(0), past, rec, Instant.now());
            earningsCache.put(symbol, c);
        }
        return c;
    }

    /** 오늘 요약이 있으면 그대로, 없으면 지금 화면의 최근 뉴스로 한 번 만든다 */
    public CompanyBriefing.AiSummary summarize(String code) {
        if (!supports(code)) throw new SummaryUnavailableException("UNSUPPORTED", "미국 종목만 요약할 수 있어요.");
        String symbol = code.toUpperCase();
        LocalDate today = LocalDate.now(KST);
        synchronized (summaryLocks.computeIfAbsent(symbol, k -> new Object())) {
            var existing = summaryRepository.findByCodeAndSummaryDate(symbol, today);
            if (existing.isPresent()) return toDto(existing.get());

            List<NewsDto> news = newsService.getRecentStockNewsFromFinnhub(symbol, NEWS_LIMIT);
            if (news.isEmpty()) throw new SummaryUnavailableException("NO_NEWS", "최근 2주 동안 요약할 기사가 없어요.");

            String text;
            try {
                text = aiProvider.getNewsSummary(symbol, news);
            } catch (AiException e) {
                // 실패한 결과는 저장하지 않는다 → 다시 누를 수 있다
                throw new SummaryUnavailableException(e.code(), e.userMessage());
            }
            if (text == null || text.isBlank() || AiProvider.ERROR_MESSAGE.equals(text)) {
                throw new SummaryUnavailableException("ERROR", "AI 요약을 받지 못했어요. 잠시 뒤 다시 눌러 주세요.");
            }
            String ids = String.join(",", news.stream().map(NewsDto::newsId).toList());
            try {
                return toDto(summaryRepository.save(new NewsSummary(symbol, today, text.strip(), ids, LocalDateTime.now(KST))));
            } catch (DataIntegrityViolationException e) {
                // 다른 서버 스레드가 먼저 저장했으면 그걸 쓴다
                return summaryRepository.findByCodeAndSummaryDate(symbol, today).map(this::toDto).orElseThrow(() -> e);
            }
        }
    }

    private CompanyBriefing.AiSummary toDto(NewsSummary s) {
        List<String> ids = s.getNewsIds() == null || s.getNewsIds().isBlank() ? List.of() : Arrays.asList(s.getNewsIds().split(","));
        return new CompanyBriefing.AiSummary(s.getContent(), s.getCreatedAt(), newsService.getFinnhubNewsByIds(ids),
                s.getSummaryDate(), s.getSummaryDate().equals(LocalDate.now(KST)));
    }

    private static boolean supports(String code) {
        return code != null && !code.isBlank() && !code.matches("^[0-9]+$");
    }
}
