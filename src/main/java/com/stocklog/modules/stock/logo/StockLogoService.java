package com.stocklog.modules.stock.logo;

import com.stocklog.modules.stock.briefing.CompanyEventProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/** 종목 로고: DB에 있으면 그대로, 없으면 Finnhub 프로필에서 받아 저장한다 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StockLogoService {

    private static final int PER_MINUTE = 30;
    private static final int MAX_CODES = 150;
    /** 한 번 요청에서 새로 받는 개수 (응답을 빨리 돌려주려고). 나머지는 pending */
    private static final int PER_REQUEST = 8;
    /** '로고 없음'은 30일 뒤 한 번 더 확인 (새로 생길 수 있음) */
    private static final long EMPTY_RECHECK_DAYS = 30;

    private final StockLogoRepository repository;
    private final CompanyEventProvider provider;

    /** 최근 1분 안의 Finnhub 호출 시각 */
    private final Deque<Instant> calls = new ConcurrentLinkedDeque<>();

    /** logos: 코드 → URL ("" = 로고 없음), pending: 아직 못 받은 코드 (잠시 뒤 다시 묻기) */
    public record Logos(Map<String, String> logos, List<String> pending) {}

    public Logos logos(List<String> codes) {
        LinkedHashSet<String> want = new LinkedHashSet<>();
        for (String c : codes == null ? List.<String>of() : codes) {
            if (c == null) continue;
            String s = c.trim().toUpperCase(Locale.ROOT);
            // 국내(숫자) 종목은 Finnhub에 로고가 거의 없어 묻지 않는다 → 화면은 글자 아이콘
            if (!s.isEmpty() && !s.matches("^[0-9]+$") && s.matches("^[A-Z0-9.\\-]{1,12}$")) want.add(s);
            if (want.size() >= MAX_CODES) break;
        }
        Map<String, String> out = new LinkedHashMap<>();
        List<String> pending = new ArrayList<>();
        if (want.isEmpty()) return new Logos(out, pending);

        Map<String, StockLogo> saved = new HashMap<>();
        repository.findAllById(want).forEach(l -> saved.put(l.getCode(), l));
        LocalDateTime now = LocalDateTime.now();
        int fetched = 0;
        for (String code : want) {
            StockLogo l = saved.get(code);
            boolean fresh = l != null && (!l.getUrl().isEmpty() || l.getCheckedAt().isAfter(now.minusDays(EMPTY_RECHECK_DAYS)));
            if (fresh) { out.put(code, l.getUrl()); continue; }
            if (fetched >= PER_REQUEST || !takeSlot()) { pending.add(code); continue; }
            fetched++;
            String url = provider.logoUrl(code);
            if (url == null) { pending.add(code); continue; }   // 실패: 저장하지 않고 다음에 다시
            if (l == null) repository.save(new StockLogo(code, url, now));
            else { l.update(url, now); repository.save(l); }
            out.put(code, url);
        }
        return new Logos(out, pending);
    }

    /** 분당 호출 한도 안이면 자리 하나를 잡는다 */
    private synchronized boolean takeSlot() {
        Instant cut = Instant.now().minusSeconds(60);
        while (!calls.isEmpty() && calls.peekFirst().isBefore(cut)) calls.pollFirst();
        if (calls.size() >= PER_MINUTE) return false;
        calls.addLast(Instant.now());
        return true;
    }
}
