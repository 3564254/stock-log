package com.stocklog.modules.stock.screening.entity;

/** 스크리닝 대상 구분 */
public enum ScreeningCategory {
    STOCK,      // 시가총액 상위 개별 종목
    INDEX_ETF   // 고정 목록의 지수 ETF (QQQ, SPY ...)
}
