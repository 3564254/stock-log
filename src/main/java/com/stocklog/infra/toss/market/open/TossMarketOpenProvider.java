package com.stocklog.infra.toss.market.open;

import com.stocklog.infra.toss.common.TossResponse;
import com.stocklog.infra.toss.config.auth.TossTokenManager;
import com.stocklog.infra.toss.market.open.TossMarketOpenOutput.Day;
import com.stocklog.infra.toss.market.open.TossMarketOpenOutput.Session;
import com.stocklog.modules.common.type.Nation;
import com.stocklog.modules.market.open.MarketOpenProvider;
import com.stocklog.modules.market.open.MarketOpenInfo;
import com.stocklog.modules.market.open.MarketOpenType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class TossMarketOpenProvider implements MarketOpenProvider {

    private static final String KR_PATH = "/api/v1/market-calendar/KR";
    private static final String US_PATH = "/api/v1/market-calendar/US";

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;
    private final TossTokenManager tossTokenManager;

    /** 장 시간은 하루 단위로만 바뀌므로 국가별로 그날 응답을 한 번만 받아 둔다 */
    private final Map<Nation, Cached> cache = new ConcurrentHashMap<>();
    private record Cached(LocalDate date, TossMarketOpenOutput output) {}

    /** 세션 하나 = 종류 + 시간 */
    private record Slot(MarketOpenType type, Session session) {}

    public TossMarketOpenProvider(@Qualifier("tossWebClient") WebClient webClient, TossTokenManager tossTokenManager) {
        this.webClient = webClient;
        this.tossTokenManager = tossTokenManager;
    }

    @Override
    public MarketOpenInfo fetchMarketOpenInfo(Nation nation) {
        OffsetDateTime now = OffsetDateTime.now(KST);
        TossMarketOpenOutput output = getCalendar(nation, now.toLocalDate());
        if (output == null) return closed(nation, null);

        // 미국장은 자정을 넘겨 이어지므로 전날·오늘·다음 영업일 세션을 시간순으로 함께 본다
        List<Slot> slots = new ArrayList<>();
        addSlots(slots, nation, output.previousBusinessDay());
        addSlots(slots, nation, output.today());
        addSlots(slots, nation, output.nextBusinessDay());

        for (Slot slot : slots) {
            if (slot.session().contains(now)) {
                return new MarketOpenInfo(nation, slot.type(),
                        toKst(slot.session().startTime()), toKst(slot.session().endTime()), true);
            }
        }
        // 장이 닫혀 있으면 다음에 열리는 세션을 알려준다
        Session next = slots.stream()
                .map(Slot::session)
                .filter(s -> s.startTime() != null && s.startTime().isAfter(now))
                .findFirst()
                .orElse(null);
        return closed(nation, next);
    }

    private TossMarketOpenOutput getCalendar(Nation nation, LocalDate today) {
        Cached cached = cache.get(nation);
        if (cached != null && cached.date().equals(today)) return cached.output();

        TossMarketOpenOutput output = callApi(nation == Nation.KR ? KR_PATH : US_PATH);
        if (output != null) cache.put(nation, new Cached(today, output));
        return output;
    }

    private static void addSlots(List<Slot> slots, Nation nation, Day day) {
        if (day == null) return;
        if (nation == Nation.KR) {
            if (day.integrated() == null) return;
            add(slots, MarketOpenType.PRE_MARKET, day.integrated().preMarket());
            add(slots, MarketOpenType.REGULAR_MARKET, day.integrated().regularMarket());
            add(slots, MarketOpenType.AFTER_MARKET, day.integrated().afterMarket());
        } else {
            add(slots, MarketOpenType.DAY_MARKET, day.dayMarket());
            add(slots, MarketOpenType.PRE_MARKET, day.preMarket());
            add(slots, MarketOpenType.REGULAR_MARKET, day.regularMarket());
            add(slots, MarketOpenType.AFTER_MARKET, day.afterMarket());
        }
    }

    private static void add(List<Slot> slots, MarketOpenType type, Session session) {
        if (session != null) slots.add(new Slot(type, session));
    }

    private static MarketOpenInfo closed(Nation nation, Session next) {
        return new MarketOpenInfo(nation, MarketOpenType.CLOSED,
                next != null ? toKst(next.startTime()) : null,
                next != null ? toKst(next.endTime()) : null,
                false);
    }

    private static LocalDateTime toKst(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(KST).toLocalDateTime();
    }

    private TossMarketOpenOutput callApi(String path) {
        TossResponse<TossMarketOpenOutput> response = webClient.get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tossTokenManager.getAccessToken())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<TossResponse<TossMarketOpenOutput>>() {})
                .block(CALL_TIMEOUT);
        return response != null ? response.getResult() : null;
    }
}
