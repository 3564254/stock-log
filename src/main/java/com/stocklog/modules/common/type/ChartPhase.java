package com.stocklog.modules.common.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ChartPhase {

    // --- [1단계: 하락 및 바닥 확인] ---
    DANGER_ZONE("위험 구간", "지속 하락하는 역배열 상태 및 신규 매수 금지"),
    DOWNTREND_CONFIRMED("하락 추세 확인", "고점·저점이 모두 낮아지고 거래량이 동반된 하락 추세 (핵심 지지선 위)"),
    BEAR_RALLY("하락장 반등", "장기 하락 추세 속 단기 반등 구간으로 추세 전환 확인 전까지 추격 매수 주의"),
    CAPITULATION("투매", "공포 매물 출회 및 진바닥 형성 신호"),
    RECOVERY_WATCH("회복 관찰", "하락 멈춤 및 초기 다중 바닥 형성 구간"),

    // --- [2단계: 상승 준비 및 타점 탐색] ---
    WATCH("관심 집중", "뚜렷한 신호는 없으나 추세가 유지되는 기본 관망 상태 또는 상승 전환 준비"),
    SETUP("셋업 형성", "변동성 축소(VCP) 등 확률 높은 상승 패턴 구축"),
    ENTRY_READY("진입 대기", "주요 저항선 돌파 직전 매수세 응축 및 타점 탐색"),

    // --- [3단계: 돌파 시나리오 및 리스크 관리] ---
    FAKE_OUT("속임수 돌파", "저항선 돌파 후 급락하는 불트랩 발생"),
    STOP_LOSS("손절", "돌파 실패 및 핵심 지지선 이탈로 인한 기계적 위기 탈출"),

    // --- [4단계: 본격 상승 및 중간 조정] ---
    STRONG_HOLD("강력 보유", "저항선 돌파 후 이평선을 타고 우상향하는 주도 추세"),
    COOLDOWN("조정 및 열기 식힘", "급등 후 이평선 근처에서 과열을 식히는 구간"),
    RE_ENTRY("재진입", "조정 후 중요 지지선 반등 및 2차 상승 랠리 초입"),

    // --- [5단계: 고점 징후 및 차익 실현] ---
    WARNING_ZONE("경고 구간", "이격도 과다 및 상승 동력 둔화 조짐 발생"),
    CLIMAX("오버슈팅", "비이성적 폭등 및 세력 차익 실현 임박 징후"),
    SCALE_OUT("분할 매도", "지지선 이탈 및 고점 갱신 실패로 인한 비중 축소"),

    // --- [기타] ---
    INSUFFICIENT_DATA("정보 부족", "차트 데이터 부족으로 판정 불가");

    private final String title;
    private final String description;
}