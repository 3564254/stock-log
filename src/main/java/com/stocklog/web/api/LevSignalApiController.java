package com.stocklog.web.api;

import com.stocklog.modules.stock.signal.LevSignalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 레버리지 단타 신호 */
@RestController
@RequestMapping("/api/lev-signal")
@RequiredArgsConstructor
public class LevSignalApiController {
    private final LevSignalService service;

    @GetMapping
    public LevSignalService.Board board() { return service.board(); }
}
