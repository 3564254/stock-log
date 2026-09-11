package com.stocklog.modules.purchase.trade.type;

import lombok.Getter;

@Getter
public enum TradeEmotion {

    // 🟢 원칙 매매 (긍정적 피드백)
    DISCIPLINED("원칙 준수", "PLAN", "기록된 시나리오와 스마트 타점을 완벽하게 이행함"),
    PATIENT_WAIT("인내심 매매", "PLAN", "원하는 타점이 올 때까지 뇌동매매를 참고 대기함"),

    // 🟡 심리 흔들림 (주의 및 모니터링 필요)
    FOMO("포모 (소외감)", "IMPULSE", "나만 두고 급등할 것 같은 공포감에 뒤늦게 진입함"),
    GREED("욕심 매매", "IMPULSE", "목표가에 도달했으나 더 큰 수익을 바라다 익절 기회를 놓침"),
    ANXIETY_SELL("조기 익절", "ANXIETY", "주가 흔들림에 불안하여 목표가 전에 너무 빨리 팔아버림"),

    // 🔴 원칙 위반 및 투매 (치명적인 손실 요인)
    IMPULSE_BUY("뇌동 매매", "IMPULSE", "계획에 없던 급등 차트를 보고 충동적으로 진입함"),
    WATERING_DOWN("무계획 물타기", "IMPULSE", "손절 원칙을 어기고 평단가를 낮추기 위해 감정적으로 추가 매수함"),
    PANIC_SELL("공포 투매", "ANXIETY", "순간적인 폭락에 겁을 먹고 계획된 손절가보다 바닥에서 던짐"),
    REVENGE_TRADE("뇌절 매매 (복수)", "IMPULSE", "이전 손실을 빠르게 만회하려 무리하게 비중을 실어 진입함");

    private final String description;    // 화면에 보여줄 한글 이름
    private final String behaviorType;   // 매매 성향 분류 (PLAN: 계획성, IMPULSE: 충동성, ANXIETY: 불안성)
    private final String detailMessage;  // 복기 꿀팁용 상세 설명

    TradeEmotion(String description, String behaviorType, String detailMessage) {
        this.description = description;
        this.behaviorType = behaviorType;
        this.detailMessage = detailMessage;
    }
}