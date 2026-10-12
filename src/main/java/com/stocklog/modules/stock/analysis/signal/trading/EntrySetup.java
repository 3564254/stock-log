package com.stocklog.modules.stock.analysis.signal.trading;

import com.stocklog.modules.stock.analysis.signal.level.PriceZone;

/**
 * 진입 유형 + 진입 구간 + 구조적 손절/목표를 묶은 내부 모델 (구 EntrySetup).
 * target1/target2/rr은 구조적 저항 구간이 없으면 NaN (임의 투영값으로 RR을 만들지 않는다).
 */
public record EntrySetup(EntryType type, PriceZone entry, double refPrice,
                         double stop, double target1, double target2, double rr) {
    public boolean hasStructuralTarget() { return Double.isFinite(target1); }
}
