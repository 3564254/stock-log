package com.stocklog.domain.stock.analysis.domain.signal.vo;

public enum MarketPhase {
    // ── 1. 하락 및 회복 국면 ──
    /** [DANGER] 주가 < 20 < 60 < 120 (역배열). 하락 추세 지속 중 매매 절대 금지 */
    DANGER_ZONE,
    /** [RECOVERY] RSI 침체권 탈출 + MACD 골든크로스. 바닥 확인 및 반등 초기 관망 */
    RECOVERY_WATCH,

    // ── 2. 진입 준비 국면 ──
    /** [WATCH] 5 > 20 > 60 (정배열 초입). 추세 전환은 되었으나 거래량 확인 대기 */
    WATCH,
    /** [SETUP] 정배열 유지 중 20일선 부근 음봉 눌림목. 변동성 축소 및 진입 대기 */
    SETUP,
    /** [ENTRY] 전고점 돌파 + 거래량 급증 + 양봉 완성. 원칙에 따른 비중 진입 */
    ENTRY_READY,

    // ── 3. 보유 국면 ──
    /** [HOLD] 주가 > 5일선 유지. 이격도 확장 및 강세 지속에 따른 수익 극대화 */
    STRONG_HOLD,

    // ── 4. 청산 및 리스크 관리 국면 ──
    /** [WARNING] RSI 70↑ 과열 + 하락 다이버전스. 익절가 상향 및 공격적 매도 준비 */
    WARNING_ZONE,
    /** [EXIT] 1차 목표가 도달. 물량 50% 익절 후 잔여분 본절가 스탑로스 대응 */
    SCALE_OUT,
    /** [PAUSE] 손절/휩소 직후 24시간 매매 정지. 심리 회복 및 뇌동매매 강제 차단 */
    COOLDOWN
}