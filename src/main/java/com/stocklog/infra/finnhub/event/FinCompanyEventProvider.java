package com.stocklog.infra.finnhub.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.stocklog.infra.finnhub.config.FinProperties;
import com.stocklog.modules.stock.briefing.CompanyBriefing;
import com.stocklog.modules.stock.briefing.CompanyEventProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Finnhub 실적 일정 · 실적 결과 */
@Component
@Slf4j
public class FinCompanyEventProvider implements CompanyEventProvider {

    private static final ZoneId NY = ZoneId.of("America/New_York");
    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    private final WebClient webClient;
    private final FinProperties finProperties;

    public FinCompanyEventProvider(@Qualifier("finnhubWebClient") WebClient webClient, FinProperties finProperties) {
        this.webClient = webClient;
        this.finProperties = finProperties;
    }

    @Override
    public List<CompanyBriefing.Earnings> upcomingEarnings(String symbol, int days) {
        try {
            LocalDate today = LocalDate.now(NY);
            CalendarOutput out = webClient.get()
                    .uri(u -> u.path("/calendar/earnings")
                            .queryParam("symbol", symbol.toUpperCase())
                            .queryParam("from", today)
                            .queryParam("to", today.plusDays(days))
                            .build())
                    .header("X-Finnhub-Token", finProperties.getApiKey())
                    .retrieve()
                    .bodyToMono(CalendarOutput.class)
                    .block(TIMEOUT);
            if (out == null || out.earningsCalendar() == null) return List.of();
            return out.earningsCalendar().stream()
                    .filter(e -> e.date() != null)
                    .map(e -> new CompanyBriefing.Earnings(LocalDate.parse(e.date()), e.hour() == null ? "" : e.hour(),
                            e.epsEstimate(), e.revenueEstimate()))
                    .filter(e -> !e.date().isBefore(today))
                    .sorted(Comparator.comparing(CompanyBriefing.Earnings::date))
                    .toList();
        } catch (Exception e) {
            log.warn("[Briefing] Finnhub 실적 일정 조회 실패 {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<CompanyBriefing.PastEarnings> recentEarnings(String symbol) {
        try {
            List<EarningsOutput> out = webClient.get()
                    .uri(u -> u.path("/stock/earnings").queryParam("symbol", symbol.toUpperCase()).build())
                    .header("X-Finnhub-Token", finProperties.getApiKey())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<EarningsOutput>>() {})
                    .block(TIMEOUT);
            if (out == null) return List.of();
            return out.stream()
                    .filter(Objects::nonNull)
                    .filter(e -> e.period() != null && e.actual() != null)
                    .map(e -> new CompanyBriefing.PastEarnings(e.period(), e.actual(), e.estimate(), e.surprisePercent()))
                    .sorted(Comparator.comparing(CompanyBriefing.PastEarnings::period).reversed())
                    .limit(4)
                    .toList();
        } catch (Exception e) {
            log.warn("[Briefing] Finnhub 실적 결과 조회 실패 {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    /** 애널리스트 추천 추이 (월별) */
    @Override
    public List<CompanyBriefing.Analysts> recommendations(String symbol) {
        try {
            List<RecOutput> out = webClient.get()
                    .uri(u -> u.path("/stock/recommendation").queryParam("symbol", symbol.toUpperCase()).build())
                    .header("X-Finnhub-Token", finProperties.getApiKey())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<RecOutput>>() {})
                    .block(TIMEOUT);
            if (out == null) return List.of();
            return out.stream()
                    .filter(Objects::nonNull)
                    .filter(r -> r.period() != null)
                    .map(r -> new CompanyBriefing.Analysts(r.period(), nz(r.strongBuy()), nz(r.buy()), nz(r.hold()), nz(r.sell()), nz(r.strongSell())))
                    .filter(a -> a.strongBuy() + a.buy() + a.hold() + a.sell() + a.strongSell() > 0)
                    .sorted(Comparator.comparing(CompanyBriefing.Analysts::period).reversed())
                    .limit(4)
                    .toList();
        } catch (Exception e) {
            log.warn("[Briefing] Finnhub 애널리스트 의견 조회 실패 {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    private static int nz(Integer v) { return v == null ? 0 : v; }

    /** 회사 프로필. 빈 객체면 Finnhub에 없는 종목 */
    @Override
    public String logoUrl(String symbol) {
        try {
            ProfileOutput p = webClient.get()
                    .uri(u -> u.path("/stock/profile2").queryParam("symbol", symbol.toUpperCase()).build())
                    .header("X-Finnhub-Token", finProperties.getApiKey())
                    .retrieve()
                    .bodyToMono(ProfileOutput.class)
                    .block(TIMEOUT);
            return p == null || p.logo() == null ? "" : p.logo().trim();
        } catch (Exception e) {
            log.warn("[Logo] Finnhub 프로필 조회 실패 {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RecOutput(String period, Integer strongBuy, Integer buy, Integer hold, Integer sell, Integer strongSell, String symbol) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProfileOutput(String logo, String name, String finnhubIndustry) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CalendarOutput(List<CalendarItem> earningsCalendar) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CalendarItem(String date, String hour, BigDecimal epsEstimate, BigDecimal revenueEstimate, String symbol) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EarningsOutput(BigDecimal actual, BigDecimal estimate, String period, BigDecimal surprisePercent, String symbol) {}
}
