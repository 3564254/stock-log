package com.stocklog.modules.stock.analysis.signal.trading;

public enum Verdict {
    ACTIONABLE,     // 진입 구간 안 + 트리거 확인 + RR 통과
    WAIT_FOR_ZONE,  // 눌림 구간까지 내려오길 대기
    WAIT_TRIGGER,   // 구간 안이지만 반전/확인 캔들 미발생
    CHASE_RISK,     // 돌파/탈환 구간을 이미 벗어남
    RR_TOO_LOW,     // 구조상 손익비 부족
    NO_SETUP        // 계획 자체가 성립하지 않음
}
