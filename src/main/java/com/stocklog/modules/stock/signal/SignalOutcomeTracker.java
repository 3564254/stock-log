package com.stocklog.modules.stock.signal;

import com.stocklog.modules.common.finance.model.ExchangeSession;
import com.stocklog.modules.stock.chart.StockChartService;
import com.stocklog.modules.stock.chart.dto.StockChartDto;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/** 신호 기록의 이후 결과를 채운다 */
@Slf4j
@Component
public class SignalOutcomeTracker {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long PAUSE_MS = 300;

    private final SignalRecordRepository repository;
    private final StockChartService chartService;
    private final TransactionTemplate tx;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "signal-outcome"); t.setDaemon(true); return t; });

    public SignalOutcomeTracker(SignalRecordRepository repository, StockChartService chartService, TransactionTemplate tx) {
        this.repository = repository; this.chartService = chartService; this.tx = tx;
    }

    @Scheduled(cron = "0 50 16 * * MON-FRI", zone = "Asia/Seoul")
    public void afterKr() { submit(); }

    @Scheduled(cron = "0 40 8 * * TUE-SAT", zone = "Asia/Seoul")
    public void afterUs() { submit(); }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() { submit(); }

    public void submit() { worker.submit(this::runSafe); }

    private void runSafe() {
        try { run(); } catch (Exception e) { log.error("[신호 결과] 갱신 실패", e); }
    }

    void run() {
        List<SignalRecord> todo = repository.findTop500ByOutcomeStatusInAndSignalDateBeforeOrderBySignalDateAsc(
                List.of("PENDING", "PARTIAL"), LocalDate.now(KST));
        if (todo.isEmpty()) return;
        Map<String, List<SignalRecord>> byCode = todo.stream().collect(Collectors.groupingBy(SignalRecord::getStockCode, LinkedHashMap::new, Collectors.toList()));
        int updated = 0;
        for (Map.Entry<String, List<SignalRecord>> en : byCode.entrySet()) {
            if (Thread.currentThread().isInterrupted()) return;
            String code = en.getKey();
            List<SignalOutcomes.Bar> bars;
            try { bars = confirmedBars(code, chartService.getDailyChartDtoAsc(code)); }
            catch (Exception e) { log.warn("[신호 결과] {} 일봉 조회 실패: {}", code, e.getMessage()); continue; }
            double cost = SignalOutcomes.costOf(code);
            LocalDateTime now = LocalDateTime.now(KST);
            List<SignalRecord> changed = new ArrayList<>();
            for (SignalRecord r : en.getValue()) {
                SignalOutcomes.Outcome o = SignalOutcomes.compute(bars, r.getSignalDate(), r.getAtr(), r.getPlanStop(), r.getPlanTarget(), cost);
                if (o.entryDate() == null) continue;
                r.applyOutcome(o, now); changed.add(r);
            }
            if (!changed.isEmpty()) { tx.executeWithoutResult(s -> repository.saveAll(changed)); updated += changed.size(); }
            try { Thread.sleep(PAUSE_MS); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
        }
        log.info("[신호 결과] {}건 중 {}건 갱신", todo.size(), updated);
    }

    /** 정규장 중이면 오늘 진행 중인 봉을 뺀다 */
    static List<SignalOutcomes.Bar> confirmedBars(String code, List<StockChartDto> daily) {
        ExchangeSession ses = ExchangeSession.fromCode(code);
        LocalDate today = LocalDate.now(ses.zone());
        boolean open = SignalRecorder.sessionOpen(ses);
        List<SignalOutcomes.Bar> out = new ArrayList<>();
        if (daily == null) return out;
        for (StockChartDto d : daily) {
            if (d.date() == null || d.open() == null || d.close() == null || d.high() == null || d.low() == null) continue;
            if (open && d.date().equals(today)) continue;
            out.add(new SignalOutcomes.Bar(d.date(), d.open().doubleValue(), d.high().doubleValue(), d.low().doubleValue(), d.close().doubleValue()));
        }
        return out;
    }

    @PreDestroy
    public void shutdown() { worker.shutdownNow(); }
}
