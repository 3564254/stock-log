package com.stocklog.modules.stock.analysis.diagnosis;



import lombok.Getter;

@Getter
public enum EntryType {
    PULLBACK("눌림목", "상승 추세 중 일시적 조정을 받아 주요 이평선이나 지지선 근처에서 반등을 준비하는 타점"),
    BREAKOUT("돌파", "의미 있는 저항선이나 박스권 상단을 강한 매수세(거래량)와 함께 뚫어내는 공격적 타점"),
    RECLAIM("탈환 및 지지", "이탈했던 중요 지지(저항)선을 재돌파한 뒤, 해당 라인을 다시 지지해주는 안정성 확인 타점"),
    NONE("미정", "진입 타점 신호 없음"),
    INSUFFICIENT_DATA("정보 부족", "차트 데이터 부족으로 판정 불가");

    private final String title;
    private final String description;

    EntryType(String title, String description) {
        this.title = title;
        this.description = description;
    }

}
