package com.stocklog.modules.stock.analysis.diagnosis.legacy.signal.vo;

import com.stocklog.modules.common.type.ChartPhase;

public record MiniSignalDto(
        ChartPhase phase,
        String phaseName,       // 예: "진입 타이밍", "관망" (배지 출력용)
        double primaryEntry,    // 희망 진입가
        double stopLoss         // 계획 손절가
) {}
