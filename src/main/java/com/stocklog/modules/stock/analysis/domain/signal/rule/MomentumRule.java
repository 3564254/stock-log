package com.stocklog.modules.stock.analysis.domain.signal.rule;


import com.stocklog.modules.stock.analysis.domain.signal.SignalResultBuilder;
import com.stocklog.modules.stock.analysis.domain.signal.vo.MarketContext;
import com.stocklog.modules.stock.analysis.domain.signal.vo.SignalWeight;
import org.springframework.stereotype.Component;

@Component
public class MomentumRule implements SignalRule {
    @Override
    public void evaluate(MarketContext ctx, SignalResultBuilder builder) {
        int macdW = SignalWeight.MACD_CROSS.getWeight() + (ctx.isStrongTrend() ? 10 : 0);
        int bbW = SignalWeight.BOLLINGER_BREAK.getWeight();
        int volW = SignalWeight.VOL_CONFIRM.getWeight();

        // 1. MACD 판별
        if (ctx.macd().isGoldenCross()) {
            builder.addBuySignal(String.format("MACD 골든크로스 (MACD: %.2f / Signal: %.2f)", ctx.macd().macd(), ctx.macd().signal()));
            builder.addBuyWeight(macdW);
        } else if (ctx.macd().isDeadCross()) {
            builder.addSellSignal(String.format("MACD 데드크로스 (MACD: %.2f / Signal: %.2f)", ctx.macd().macd(), ctx.macd().signal()));
            builder.addSellWeight(SignalWeight.MACD_CROSS.getWeight());
        }

        // 2. 볼린저 밴드 이탈 확인
        if (ctx.currentPrice() < ctx.lower()) {
            if (ctx.isVolBearish()) {
                builder.addSellSignal(String.format("🚨 볼린저 하단 강한 이탈 (투매 주의) %%B: %.2f", ctx.percentB()));
                builder.addSellWeight(bbW * 2); // 가중치 2배 (위험)
            } else {
                builder.addBuySignal(String.format("볼린저 하단 이탈 (단기 낙폭과대) %%B: %.2f", ctx.percentB()));
                builder.addBuyWeight(bbW);
            }
        }
        else if (ctx.currentPrice() > ctx.upper()) {
            if (ctx.isVolBullish()) {
                if (ctx.isSqueeze()) {
                    // 스퀴즈 상태에서 거래량을 동반한 상단 돌파는 초강력 시그널
                    builder.addBuySignal(String.format("🚀 [스퀴즈 폭발] 볼린저 밴드 응축 후 상단 돌파 (상단 %.0f)", ctx.upper()));
                    builder.addBuyWeight(bbW * 2 + volW);
                } else {
                    builder.addBuySignal(String.format("볼린저 상단 돌파 (거래량 동반) — 강한 추세 (상단 %.0f)", ctx.upper()));
                    builder.addBuyWeight(bbW + volW);
                }
            } else {
                builder.addSellSignal(String.format("볼린저 상단 이탈 (단기 과열) (현재 %.0f / 상단 %.0f)", ctx.currentPrice(), ctx.upper()));
                builder.addSellWeight(bbW);
            }
        }
    }
}