package com.stocklog.modules.stock.analysis.signal;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SignalStrengthBuilder {

    /** 불변 결과 스냅샷 */
    public record SignalResult(
            List<String> buySignals,
            List<String> sellSignals,
            List<String> watchSignals,
            int buyStrength,
            int sellStrength) {}

    private static final int MAX_STRENGTH = 100;

    // 삽입 순서 유지 + 동일 문구 중복 방지
    private final Set<String> buySignals = new LinkedHashSet<>();
    private final Set<String> sellSignals = new LinkedHashSet<>();
    private final Set<String> watchSignals = new LinkedHashSet<>();
    private int buyWeight;
    private int sellWeight;

    // ── 신호 + 가중치를 한 번에 ──
    public void addBuySignal(String signal, int weight) {
        addBuySignal(signal);
        addBuyWeight(weight);
    }

    public void addSellSignal(String signal, int weight) {
        addSellSignal(signal);
        addSellWeight(weight);
    }

    // ── 신호만 ──
    public void addBuySignal(String signal)   { addIfPresent(buySignals, signal); }
    public void addSellSignal(String signal)  { addIfPresent(sellSignals, signal); }
    /** 매수/매도 어느 쪽도 아닌 관찰 신호 (가중치 없음) */
    public void addWatchSignal(String signal) { addIfPresent(watchSignals, signal); }

    // ── 가중치만 ──
    public void addBuyWeight(int weight)  { this.buyWeight += weight; }
    public void addSellWeight(int weight) { this.sellWeight += weight; }

    private static void addIfPresent(Set<String> target, String signal) {
        if (signal != null && !signal.isBlank()) {
            target.add(signal);
        }
    }

    // ── 결과 ──
    public SignalResult build() {
        return new SignalResult(
                List.copyOf(buySignals),
                List.copyOf(sellSignals),
                List.copyOf(watchSignals),
                clamp(buyWeight),
                clamp(sellWeight));
    }

    public boolean hasConflict() {
        return !buySignals.isEmpty() && !sellSignals.isEmpty();
    }

    // 기존 호출부 호환용 getter
    public List<String> getBuySignals()   { return List.copyOf(buySignals); }
    public List<String> getSellSignals()  { return List.copyOf(sellSignals); }
    public List<String> getWatchSignals() { return List.copyOf(watchSignals); }
    public int getClampedBuyStrength()    { return clamp(buyWeight); }
    public int getClampedSellStrength()   { return clamp(sellWeight); }
    public int getRawBuyWeight()          { return buyWeight; }
    public int getRawSellWeight()         { return sellWeight; }

    private static int clamp(int v) {
        return Math.min(Math.max(v, 0), MAX_STRENGTH);
    }
}

