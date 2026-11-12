package com.stocklog.modules.stock.momentum;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 미국 장 마감 뒤 하루 1번 모멘텀 순위 계산 */
@Slf4j
@Component
public class MomentumScheduler {

    private final MomentumService service;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "momentum");
        t.setDaemon(true);
        return t;
    });

    public MomentumScheduler(MomentumService service) { this.service = service; }

    @Scheduled(cron = "0 40 7 * * TUE-SAT", zone = "Asia/Seoul")
    public void daily() { submit(); }

    /** 처음 띄웠을 때 순위가 없으면 바로 한 번 */
    @EventListener(ApplicationReadyEvent.class)
    public void initIfEmpty() {
        try {
            if (!service.hasData()) submit();
        } catch (Exception e) {
            log.warn("[Momentum] 초기 확인 실패: {}", e.getMessage());
        }
    }

    public void submit() {
        if (service.isRunning()) return;
        worker.submit(() -> {
            try { service.refresh(); } catch (Exception e) { log.error("[Momentum] 작업 실패", e); }
        });
    }

    @PreDestroy
    public void shutdown() { worker.shutdownNow(); }
}
