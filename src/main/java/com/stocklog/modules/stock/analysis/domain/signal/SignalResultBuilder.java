package com.stocklog.modules.stock.analysis.domain.signal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// 개별 SignalRule들이 평가한 신호와 가중치를 누적하는 빌더 클래스
public class SignalResultBuilder {
    // TODO(패턴): 이름은 Builder지만 완성 객체를 build()하는 GoF Builder보다는
    // 규칙 실행 중 상태를 누적하는 accumulator에 가깝다. Builder로 유지한다면 최종 결과 생성 책임을
    // 이 객체에 둘지, 아니라면 역할이 드러나는 이름으로 바꿀지 팀 규칙을 정해 보자.

    // ── 상태(State) 변수 ──
    private final List<String> buySignals;
    private final List<String> sellSignals;
    private int buyWeight;
    private int sellWeight;

    public SignalResultBuilder() {
        this.buySignals = new ArrayList<>();
        this.sellSignals = new ArrayList<>();
        this.buyWeight = 0;
        this.sellWeight = 0;
    }

    // ── 1. 데이터 누적용 메서드 (SignalRule 내부에서 호출) ──

    public void addBuySignal(String signal) {
        if (signal != null && !signal.isBlank()) {
            this.buySignals.add(signal);
        }
    }

    public void addSellSignal(String signal) {
        if (signal != null && !signal.isBlank()) {
            this.sellSignals.add(signal);
        }
    }

    public void addBuyWeight(int weight) {
        this.buyWeight += weight;
    }

    public void addSellWeight(int weight) {
        this.sellWeight += weight;
    }

    // ── 2. 최종 결과 반환용 Getter (EntrySignalAnalyzer 에서 호출) ──

    /**
     * 외부에서 리스트를 임의로 조작하지 못하도록 불변(Unmodifiable) 리스트로 반환합니다.
     */
    public List<String> getBuySignals() {
        return Collections.unmodifiableList(buySignals);
    }

    public List<String> getSellSignals() {
        return Collections.unmodifiableList(sellSignals);
    }

    /**
     * 매수 강도를 0 ~ 100 사이로 클램핑(제한)하여 반환합니다. (기존 로직 반영)
     */
    public int getClampedBuyStrength() {
        return Math.min(Math.max(buyWeight, 0), 100);
    }

    /**
     * 매도 강도를 0 ~ 100 사이로 클램핑(제한)하여 반환합니다. (기존 로직 반영)
     */
    public int getClampedSellStrength() {
        return Math.min(Math.max(sellWeight, 0), 100);
    }

    // 원본 점수가 필요할 경우를 대비한 Getter
    public int getRawBuyWeight() { return buyWeight; }
    public int getRawSellWeight() { return sellWeight; }
}
