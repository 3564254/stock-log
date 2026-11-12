package com.stocklog.modules.stock.signal;

/** 신호 기록의 버전. 엔진 판정 규칙이나 결과 추적 규칙을 바꾸면 여기 값을 올려 새 기록을 따로 모은다 */
public final class SignalVersion {
    private SignalVersion() {}

    /** 차트 엔진: 진입 손익비 하한 2.0, 2026-10-26 기준 판정 로직 */
    public static final String ENGINE = "engine-2026.10.26";

    /** 결과 추적 규칙 */
    public static final String TRACK = "track-v1";
}
