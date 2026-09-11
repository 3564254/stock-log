package com.stocklog.modules.stock.analysis.domain.signal.rule;

import com.stocklog.modules.stock.analysis.domain.signal.SignalResultBuilder;
import com.stocklog.modules.stock.analysis.domain.signal.vo.MarketContext;
import com.stocklog.modules.stock.analysis.domain.signal.vo.SignalWeight;
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

        // 💡 피벗 기준선(PP) 상/하향 돌파 판별 로직 추가
        double pp = ctx.dailyPivot().p();
        if (ctx.currentPrice() > pp && ctx.yesterdayPrice() <= pp) {
            builder.addBuySignal(String.format("피벗 기준선 상향 돌파 — 단기 추세 전환 (PP: %.0f)", pp));
            builder.addBuyWeight(pivotW);
        } else if (ctx.currentPrice() < pp && ctx.yesterdayPrice() >= pp) {
            builder.addSellSignal(String.format("피벗 기준선 하향 이탈 — 단기 하방 압력 (PP: %.0f)", pp));
            builder.addSellWeight(pivotW);
        }

        if (ctx.currentPrice() <= ctx.dailyPivot().s2() * 1.01) {
            builder.addBuySignal(String.format("피벗 2차 지지선 도달 — 강한 반등 기대 (S2: %.0f)", ctx.dailyPivot().s2()));
            builder.addBuyWeight(pivotW);
        }
        if (ctx.currentPrice() >= ctx.dailyPivot().r2() * 0.99) {
            builder.addSellSignal(String.format("피벗 2차 저항선 도달 — 과열 주의 (R2: %.0f)", ctx.dailyPivot().r2()));
            builder.addSellWeight(pivotW);
        }

        // 3. 매물대 (POC) 지지/저항 테스트
        // 💡 매직 넘버 분리 (추후 SignalWeight Enum에 POC_SUPPORT 등으로 추가 권장)
        int pocW = 10;
        double pocDist = (ctx.currentPrice() - ctx.pocPrice()) / ctx.pocPrice() * 100;
        if (Math.abs(pocDist) <= 2.0) { // 현재가가 매물대 2% 이내에 근접했을 때
            if (ctx.currentPrice() >= ctx.pocPrice()) {
                builder.addBuySignal(String.format("🎯 [매물대 지지] 최대 매물대(POC: %.0f) 안착 테스트 중", ctx.pocPrice()));
                builder.addBuyWeight(pocW);
            } else {
                builder.addSellSignal(String.format("🧱 [매물대 저항] 최대 매물대(POC: %.0f) 하단 진입 — 돌파 확인 필요", ctx.pocPrice()));
                builder.addSellWeight(pocW);
            }
        }

        // 4. 거래량 가중 평균가 (VWAP) 돌파 여부
        // 💡 매직 넘버 분리 (추후 SignalWeight Enum에 VWAP_BREAK 등으로 추가 권장)
        int vwapW = 15;
        if (ctx.currentPrice() > ctx.vwap20() && ctx.yesterdayPrice() <= ctx.vwap20()) {
            builder.addBuySignal(String.format("🚀 [세력선 돌파] 기관 평균단가(VWAP: %.0f) 상향 돌파", ctx.vwap20()));
            builder.addBuyWeight(vwapW);
        } else if (ctx.currentPrice() < ctx.vwap20() && ctx.yesterdayPrice() >= ctx.vwap20()) {
            builder.addSellSignal(String.format("📉 [세력선 이탈] 기관 평균단가(VWAP: %.0f) 하향 이탈", ctx.vwap20()));
            builder.addSellWeight(vwapW);
        }
    }
}