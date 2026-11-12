package com.stocklog.modules.stock.screening.entity;

/** 진단용: 타점 엔진의 진입 계획 관문 중 어디까지 갔는지 */
public enum ScreeningStage {
    INSUFFICIENT_DATA,  // 차트 데이터 부족
    PHASE_BLOCKED,      // 진입 계획을 세우지 않는 국면
    NO_ENTRY_TYPE,
    NO_SETUP,           // 국면은 통과했지만 쓸 만한 진입 구간이 없음
    PLANNED             // 진입 계획까지 나옴 → verdict 로 판정
}
