package com.stocklog.modules.market.domain.type;

public enum MacroRegimeType {
    RECOVERY,     // 회복기
    EXPANSION,    // 확장기
    LATE_CYCLE,   // 후퇴/둔화기 (현재)
    CONTRACTION,  // 수축/침체기
    UNKNOWN       // 예외 처리용
}
