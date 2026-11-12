package com.stocklog.modules.market.news;

/** 뉴스 제공처. 출처마다 언어·카테고리 체계·필드(원문 링크, 요약, 이미지)가 달라서 화면에서는 출처별로 따로 보여 준다 */
public enum NewsSource {
    KIS, FINNHUB, UNKNOWN
}
