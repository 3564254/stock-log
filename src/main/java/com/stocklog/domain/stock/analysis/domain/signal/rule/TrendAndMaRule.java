package com.stocklog.domain.stock.analysis.domain.signal.rule;
import com.stocklog.domain.stock.analysis.domain.signal.SignalResultBuilder;
import com.stocklog.domain.stock.analysis.domain.signal.vo.MarketContext;
import com.stocklog.domain.stock.analysis.domain.signal.vo.SignalWeight;
import org.springframework.stereotype.Component;

@Component
public class TrendAndMaRule implements SignalRule {
    @Override
    public void evaluate(MarketContext ctx, SignalResultBuilder builder) {
        int weight = SignalWeight.MA_CROSS.getWeight();

        // 1. 단기 추세 (MA5) 분석
        if (ctx.currentPrice() > ctx.ma5()) {
            if (ctx.isMa5Rising()) {
                if (ctx.distFromMa5() > ctx.dynamicThreshold()) {
                    builder.addSellSignal(String.format("[이격과열] 5일선 대비 +%.1f%% — 조정 주의", ctx.distFromMa5()));
                    builder.addSellWeight(weight);
                } else {
                    builder.addBuySignal(String.format("[5일선 상방] 안착 및 탄력 유지 (기준: %.0f)", ctx.ma5()));
                    builder.addBuyWeight(weight);
                }
            } else {
                builder.addBuySignal(String.format("5일선 돌파(기울기 하락) — 안착 확인 필요 (MA5: %.0f)", ctx.ma5()));
                builder.addBuyWeight(weight / 2);
            }
        } else {
            if (!ctx.isMa5Rising()) {
                if (Math.abs(ctx.distFromMa5()) > ctx.dynamicThreshold()) {
                    builder.addBuySignal(String.format("[과대낙폭] 5일선 이격 %.1f%% — 반등 기대", ctx.distFromMa5()));
                    builder.addBuyWeight(weight);
                } else {
                    builder.addSellSignal(String.format("[5일선 하방] 단기 추세 꺾임 (기준: %.0f)", ctx.ma5()));
                    builder.addSellWeight(weight);
                }
            }
        }

        // 2. 피벗 지지/저항 라인 확인
        int pivotW = SignalWeight.PIVOT_LEVEL.getWeight();
        if (ctx.currentPrice() <= ctx.dailyPivot().s2() * 1.01) {
            builder.addBuySignal(String.format("피벗 2차 지지선 도달 — 강한 반등 기대 (S2: %.0f)", ctx.dailyPivot().s2()));
            builder.addBuyWeight(pivotW);
        }
        if (ctx.currentPrice() >= ctx.dailyPivot().r2() * 0.99) {
            builder.addSellSignal(String.format("피벗 2차 저항선 도달 — 과열 주의 (R2: %.0f)", ctx.dailyPivot().r2()));
            builder.addSellWeight(pivotW);
        }
    }
}