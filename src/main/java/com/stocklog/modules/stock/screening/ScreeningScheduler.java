package com.stocklog.modules.stock.screening;

import com.stocklog.modules.common.finance.model.ExchangeSession;
import com.stocklog.modules.common.type.Nation;
import com.stocklog.modules.stock.signal.SignalRecordRepository;
import com.stocklog.modules.stock.signal.SignalVersion;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 장 마감 뒤 하루 한 번 지수 ETF와 시가총액 상위 종목(국내 50, 미국 120)을 분석한다 */
@Slf4j
@Component
public class ScreeningScheduler {

    static final int KR_SIZE = 50;
    static final int US_SIZE = 120;

    private final ScreeningService screeningService;
    private final SignalRecordRepository signalRecords;
    private final Set<Nation> queued = ConcurrentHashMap.newKeySet();   // 대기·실행 중인 국가
    private final java.util.Map<Nation, LocalDateTime> tried = new ConcurrentHashMap<>();   // 보완 분석을 이미 시도한 장 마감
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "screening");
        t.setDaemon(true);
        return t;
    });

    public ScreeningScheduler(ScreeningService screeningService, SignalRecordRepository signalRecords) {
        this.screeningService = screeningService;
        this.signalRecords = signalRecords;
    }

    /** 국내: 정규장 15:30 마감, 종가 확정 뒤 */
    @Scheduled(cron = "0 10 16 * * MON-FRI", zone = "Asia/Seoul")
    public void screenKr() {
        submit(Nation.KR, KR_SIZE);
    }

    /** 미국: 16:00(뉴욕) 마감 = 한국 05:00(서머타임) / 06:00 → 넉넉히 07:10 */
    @Scheduled(cron = "0 10 7 * * TUE-SAT", zone = "Asia/Seoul")
    public void screenUs() {
        submit(Nation.US, US_SIZE);
    }

    /** 서버를 켰을 때: 결과가 없으면 바로 한 번 돌리고, 있으면 놓친 날 보완(catchUp)을 확인한다 */
    @EventListener(ApplicationReadyEvent.class)
    public void screenIfEmpty() {
        for (Nation nation : new Nation[]{Nation.KR, Nation.US}) {
            try {
                if (!screeningService.hasCurrentResult(nation)) submit(nation, nation == Nation.KR ? KR_SIZE : US_SIZE);
                else catchUp(nation);
            } catch (Exception e) {
                log.warn("[Screening] {} 결과 확인 실패: {}", nation, e.getMessage());
            }
        }
    }

    /** 놓친 날 보완: 매시 25분에도 확인한다 */
    @Scheduled(cron = "0 25 * * * *", zone = "Asia/Seoul")
    public void catchUpHourly() {
        for (Nation nation : new Nation[]{Nation.KR, Nation.US}) {
            try { catchUp(nation); } catch (Exception e) { log.warn("[Screening] {} 보완 확인 실패: {}", nation, e.getMessage()); }
        }
    }

    void catchUp(Nation nation) {
        ExchangeSession ses = nation == Nation.KR ? ExchangeSession.KR : ExchangeSession.US;
        ZonedDateTime now = ZonedDateTime.now(ses.zone());
        if (inSession(ses, now)) return;
        ZonedDateTime lastClose = lastSessionClose(ses, now).plusMinutes(10);   // 종가 반영 여유
        LocalDateTime afterKst = lastClose.withZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime();
        if (afterKst.equals(tried.get(nation))) return;
        if (signalRecords.existsByNationAndEngineVersionAndRecordedAtAfter(nation, SignalVersion.ENGINE, afterKst)) return;
        tried.put(nation, afterKst);
        log.info("[Screening] {} {} 이후 신호 기록이 없어 보완 분석을 돌립니다", nation, afterKst);
        submit(nation, nation == Nation.KR ? KR_SIZE : US_SIZE);
    }

    /** 평일 정규장 시간인지 (휴장일은 모름) */
    static boolean inSession(ExchangeSession ses, ZonedDateTime now) {
        DayOfWeek d = now.getDayOfWeek();
        if (d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY) return false;
        LocalTime t = now.toLocalTime();
        return !t.isBefore(ses.open()) && t.isBefore(ses.open().plusMinutes(ses.minutes()));
    }

    /** 지금 시각 이전에 끝난 가장 최근 평일 정규장의 마감 시각 */
    static ZonedDateTime lastSessionClose(ExchangeSession ses, ZonedDateTime now) {
        LocalTime close = ses.open().plusMinutes(ses.minutes());
        ZonedDateTime c = now.toLocalDate().atTime(close).atZone(ses.zone());
        if (c.isAfter(now)) c = c.minusDays(1);
        while (c.getDayOfWeek() == DayOfWeek.SATURDAY || c.getDayOfWeek() == DayOfWeek.SUNDAY) c = c.minusDays(1);
        return c;
    }

    private void submit(Nation nation, int size) {
        if (!queued.add(nation)) return;   // 이미 대기·실행 중
        worker.submit(() -> {
            try {
                screeningService.run(nation, size);
            } catch (Exception e) {
                log.error("[Screening] {} 분석 작업 실패", nation, e);
            } finally {
                queued.remove(nation);
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        worker.shutdownNow();
    }
}
