package com.stocklog.modules.stock.briefing;

import com.stocklog.modules.market.news.dto.NewsDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 분석 화면 '회사 소식' 탭. 사실만 모은다 (요약·호재 판정 없음) */
public record CompanyBriefing(
        boolean supported,               // 미국 종목만 true
        Earnings nextEarnings,           // 다음 실적 발표 (모르면 null)
        List<PastEarnings> recentEarnings,
        List<NewsDto> news,
        AiSummary aiSummary,             // 오늘 만든 AI 요약 (없으면 null)
        List<Analysts> analysts          // 애널리스트 의견 월별, 최신순 최대 4개
) {
    /** period: 집계 월(yyyy-MM-dd). Finnhub /stock/recommendation */
    public record Analysts(String period, int strongBuy, int buy, int hold, int sell, int strongSell) {}

    /** AI 요약 본문과 인용한 기사 목록 */
    public record AiSummary(String content, java.time.LocalDateTime createdAt, List<NewsDto> sources,
                            LocalDate summaryDate, boolean today) {}

    /** date: 미국 현지 날짜, hour: bmo(장 시작 전) · amc(장 마감 후) · dmh(장중) · "" */
    public record Earnings(LocalDate date, String hour, BigDecimal epsEstimate, BigDecimal revenueEstimate) {}

    /** period: 분기 말일(yyyy-MM-dd), surprisePercent: 예상 대비 % */
    public record PastEarnings(String period, BigDecimal actual, BigDecimal estimate, BigDecimal surprisePercent) {}

    public static CompanyBriefing unsupported() {
        return new CompanyBriefing(false, null, List.of(), List.of(), null, List.of());
    }
}
