package com.stocklog.modules.market.news;

/**
 * 뉴스 제공처. 출처마다 언어·카테고리 체계·필드(원문 링크, 요약, 이미지)가 달라서
 * 화면에서는 출처별로 따로 보여 준다.
 *  - KIS     : 한국어 헤드라인만 (원문 링크·요약 없음) → 메인 "실시간 핵심 뉴스"
 *  - FINNHUB : 영어 원문 (요약·이미지·링크)          → 시장 페이지 뉴스 탭
 */
public enum NewsSource {
    KIS, FINNHUB, UNKNOWN
}
