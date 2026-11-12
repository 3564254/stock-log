package com.stocklog.modules.stock.analysis.signal;

import java.util.List;

/** 전체 시그널 탭의 영역 하나 */
public record SignalGroup(
        String title,
        String desc,
        int score,
        int maxScore,
        List<SignalView> items,
        long goodCount,
        long badCount
) {
    public static SignalGroup of(String title, String desc, int score, int maxScore, List<String> signals) {
        List<SignalView> items = SignalView.listOf(signals);
        return new SignalGroup(title, desc, score, maxScore, items,
                SignalView.count(items, SignalView.Tone.GOOD),
                SignalView.count(items, SignalView.Tone.BAD));
    }

    /** 점수 막대 너비 (0~100) */
    public int percent() {
        if (maxScore <= 0) return 0;
        return Math.max(0, Math.min(100, Math.round(score * 100f / maxScore)));
    }
}
