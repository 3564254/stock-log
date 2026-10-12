package com.stocklog.modules.stock.analysis.signal.trading;

import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.stock.analysis.signal.SignalStrengthBuilder;
import com.stocklog.modules.stock.analysis.signal.level.PriceZone;
import com.stocklog.modules.stock.analysis.signal.level.SignalInputs;

import java.util.Comparator;
import java.util.List;

import static com.stocklog.modules.common.finance.indicator.phase.PhaseConst.*;

/** 진입 판정의 문구 + 매수 가중치. 매수 가중치는 ACTIONABLE일 때만 부여한다. */
public final class EntrySignalText {

    private EntrySignalText() {}

    public static void report(Verdict verdict, EntrySetup p, SignalStrengthBuilder b) {
        String zone = String.format("진입 구간 %s~%s (%s)",
                fmt(p.entry().low()), fmt(p.entry().high()), String.join("·", p.entry().sources()));
        String risk = String.format("구조적 손절 %s", fmt(p.stop()));
        String tgt = p.hasStructuralTarget()
                ? String.format("1차 목표 %s, RR %.1f", fmt(p.target1()), p.rr())
                : "위쪽에 뚜렷한 저항 구간 없음(RR 산출 불가)";
        String label = p.type().getTitle();

        switch (verdict) {
            case ACTIONABLE -> b.addBuySignal(
                    String.format("🎯 [%s 진입 구간] %s / %s / %s", label, zone, risk, tgt),
                    weight(p));
            case WAIT_FOR_ZONE -> b.addWatchSignal(
                    String.format("⏳ [%s 대기] 현재가가 구간 위. %s / %s", label, zone, risk));
            case WAIT_TRIGGER -> b.addWatchSignal(
                    String.format("⏳ [%s 확인 대기] 구간 안이나 반등 확인(양봉) 전. %s", label, zone));
            case CHASE_RISK -> b.addWatchSignal(
                    String.format("⚠️ [추격 주의] %s 구간을 이미 벗어남. %s", label, zone));
            case RR_TOO_LOW -> b.addWatchSignal(
                    String.format("⚖️ [손익비 부족] %s. %s / %s", label, risk, tgt));
            case NO_SETUP -> { }
        }
    }

    /** 진입 계획이 없거나 대기 중일 때: 가까운 지지/저항 구간과 과열 여부를 관찰 신호로 남긴다. */
    public static void explain(SignalInputs in, List<PriceZone> zones, MarketContext ctx, SignalStrengthBuilder b) {
        double price = in.price();

        zones.stream()
                .filter(z -> z.high() < price && z.strength() >= MIN_SUPPORT_STRENGTH)
                .max(Comparator.comparingDouble(PriceZone::high))
                .ifPresent(z -> b.addWatchSignal(String.format(
                        "🧭 [가까운 지지] %s~%s (%s), 현재가 대비 %+.1f%%",
                        fmt(z.low()), fmt(z.high()), String.join("·", z.sources()),
                        (z.high() - price) / price * 100)));

        zones.stream()
                .filter(z -> z.low() > price && z.strength() >= MIN_TARGET_STRENGTH)
                .min(Comparator.comparingDouble(PriceZone::low))
                .ifPresent(z -> b.addWatchSignal(String.format(
                        "🧭 [위쪽 저항] %s~%s (%s), 현재가 대비 %+.1f%%",
                        fmt(z.low()), fmt(z.high()), String.join("·", z.sources()),
                        (z.low() - price) / price * 100)));

        double rsi = ctx.momentum().rsi();
        if (Double.isFinite(rsi) && rsi >= WARNING_RSI) {
            b.addWatchSignal(String.format(
                    "🌡️ [과열권] RSI %.0f. 신규 진입은 지지 구간까지 눌린 뒤 거래량 감소와 반등이 확인될 때 판단", rsi));
        }
    }

    private static int weight(EntrySetup p) {
        double base = switch (p.type()) {
            case PULLBACK -> BASE_PULLBACK;
            case BREAKOUT -> BASE_BREAKOUT;
            case RECLAIM  -> BASE_RECLAIM;
            case NONE, INSUFFICIENT_DATA -> 0.0;
        };
        // 구조적 목표가 없으면 RR을 검증할 수 없으므로 절반만 반영
        double factor = p.hasStructuralTarget() ? Math.min(1.3, Math.max(0.6, p.rr() / 2.0)) : 0.5;
        return (int) Math.round(base * factor);
    }

    private static String fmt(double v) {
        return v >= 1000 ? String.format("%,.0f", v) : String.format("%,.2f", v);
    }
}
