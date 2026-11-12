package com.stocklog.web.api;

import com.stocklog.modules.common.type.Nation;
import com.stocklog.modules.stock.signal.SignalStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 신호 성과 (전향적 기록, 가상 추적) */
@RestController
@RequestMapping("/api/signals")
@RequiredArgsConstructor
public class SignalApiController {
    private final SignalStatsService statsService;

    @GetMapping("/stats")
    public SignalStatsService.Stats stats(@RequestParam(defaultValue = "US") String nation) {
        return statsService.stats("KR".equalsIgnoreCase(nation) ? Nation.KR : Nation.US);
    }
}
