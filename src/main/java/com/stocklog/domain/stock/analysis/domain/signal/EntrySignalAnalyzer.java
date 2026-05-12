package com.stocklog.domain.stock.analysis.domain.signal;

import com.stocklog.domain.stock.analysis.domain.signal.rule.SignalRule;
import com.stocklog.domain.stock.analysis.domain.signal.vo.MarketContext;
import com.stocklog.domain.stock.analysis.domain.signal.vo.MarketPhase;
import com.stocklog.domain.stock.analysis.domain.signal.vo.EntrySignal;
import com.stocklog.domain.stock.chart.dto.internal.StockChartDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class EntrySignalAnalyzer {

    private final List<SignalRule> signalRules;

    public EntrySignal analyze(List<StockChartDto> dailyCharts, List<StockChartDto> weeklyCharts) {

        MarketContext ctx = MarketContext.getMarketContext(dailyCharts, weeklyCharts);

        if (ctx == null) {
            return EntrySignal.builder()
                    .buySignals(List.of("데이터 부족 — 일봉 50개 이상 필요"))
                    .sellSignals(List.of())
                    .build();
        }

        SignalResultBuilder builder = new SignalResultBuilder();
        for (SignalRule rule : signalRules) {
            rule.evaluate(ctx, builder);
        }

        // 3. 시장 국면(Phase) 판별 (9단계 확장 적용)
        MarketPhase currentPhase = determinePhase(ctx);
        addPhaseSpecificSignals(currentPhase, ctx, builder);

        PricingResult pricing = calculateTargets(ctx, currentPhase);
        String verdict = generateVerdict(ctx, currentPhase, pricing);

        return EntrySignal.builder()
                .buySignals(builder.getBuySignals())
                .sellSignals(builder.getSellSignals())
                .buyStrength(builder.getClampedBuyStrength())
                .sellStrength(builder.getClampedSellStrength())
                .marketContext(ctx)
                .primaryEntry(pricing.primaryEntry)
                .secondaryEntry(pricing.secondaryEntry)
                .stopLoss(pricing.stopLoss)
                .phase(currentPhase)
                .primaryTarget(pricing.primaryTarget)
                .secondaryTarget(pricing.secondaryTarget)
                .trailingStop(pricing.trailingStop)
                .riskPerShare(pricing.riskPerShare)
                .recommendedWeight(pricing.recWeight)
                .verdict(verdict)
                .build();
    }

    // ── 시장 국면(Phase) 판별 로직 (미사용 Enum 활용) ──
    private MarketPhase determinePhase(MarketContext ctx) {
        boolean isBullishCandle = ctx.currentPrice() > ctx.yesterdayPrice();

        // 1. 익절 및 경고 국면 (가장 먼저 필터링하여 과열 방어)
        if (ctx.rsi() >= 70 && !ctx.macd().isHistogramRising()) {
            return MarketPhase.WARNING_ZONE; // 과매수권 + 모멘텀 둔화
        } else if (ctx.isUptrend() && ctx.currentPrice() >= ctx.upper() && ctx.isVolBullish()) {
            return MarketPhase.SCALE_OUT; // 정배열 중 볼린저 상단 강한 돌파 (오버슈팅/익절구간)
        }

        // 2. 하락 및 바닥 확인 국면
        if (ctx.currentPrice() < ctx.ma50() || (!ctx.isMa20Rising() && ctx.currentPrice() < ctx.ma20())) {
            // 패닉 투매 이탈 확인
            if (ctx.currentPrice() < ctx.lower() && ctx.isVolBearish()) {
                return MarketPhase.COOLDOWN; // 하단 이탈 + 대량 매도 (손절/휩소 후 쿨다운)
            }
            // 낙폭 과대 후 반등 징후 포착
            if (ctx.macd().isGoldenCross() && ctx.rsi() < 45) {
                return MarketPhase.RECOVERY_WATCH; // 침체권 탈출 및 골든크로스 발생
            }
            return MarketPhase.DANGER_ZONE; // 일반적인 역배열 지속
        }

        // 3. 방향성 탐색 국면
        if (!ctx.isUptrend()) {
            return MarketPhase.WATCH;
        }

        // 4. 상승 추세 국면
        if (ctx.adx() >= 30 && ctx.isMa20Rising() && ctx.currentPrice() > ctx.ma5()) {
            return MarketPhase.STRONG_HOLD;
        } else if (!isBullishCandle || (!ctx.isVolBullish() && ctx.currentPrice() > ctx.upper())) {
            return MarketPhase.SETUP;
        } else {
            return MarketPhase.ENTRY_READY;
        }
    }

    private void addPhaseSpecificSignals(MarketPhase phase, MarketContext ctx, SignalResultBuilder builder) {
        if (ctx.isUptrend() && phase != MarketPhase.DANGER_ZONE && phase != MarketPhase.WATCH) {
            builder.addBuySignal("✅ [추세 확립] 정배열 및 20일선 우상향 기울기 확보");
        }

        switch (phase) {
            case ENTRY_READY -> builder.addBuySignal("🛡️ [타점 확정] 브레이크 확인 및 거래량 지지 포착");
            case STRONG_HOLD -> builder.addBuySignal("🚀 [불장 탑승] 초강세 추세 지속, 5일선 추세추종 모드 전환");
            case RECOVERY_WATCH -> builder.addBuySignal("🌱 [바닥 확인] 과매도권 탈출 및 MACD 모멘텀 호전");
            case WARNING_ZONE -> builder.addSellSignal("⚠️ [과열 경고] RSI 70 상회 및 모멘텀 상승세 둔화");
            case SCALE_OUT -> builder.addSellSignal("💰 [오버슈팅] 단기 저항선 및 볼린저 상단 도달 (분할 익절 권장)");
            case COOLDOWN -> builder.addSellSignal("🧊 [투매 발생] 주요 지지선 이탈 및 패닉 셀링 동반");
        }
    }

    // ── 타점 및 리스크 관리 산출 로직 ──
    private PricingResult calculateTargets(MarketContext ctx, MarketPhase phase) {
        double atr = ctx.atr();
        double cur = ctx.currentPrice();

        List<Double> upResistances = Arrays.asList(ctx.dailyPivot().r1(), ctx.upper(), ctx.dailyPivot().r2())
                .stream()
                .filter(p -> !Double.isNaN(p) && p > cur)
                .sorted()
                .collect(Collectors.toList());

        double primaryEntry, secondaryEntry, stopLoss, primaryTarget, secondaryTarget, trailingStop;

        // SCALE_OUT, WARNING_ZONE 국면에서는 트레일링 스탑을 매우 타이트하게(MA5) 설정하여 수익 보존
        if (phase == MarketPhase.STRONG_HOLD || phase == MarketPhase.SCALE_OUT || phase == MarketPhase.WARNING_ZONE) {
            primaryEntry   = Math.max(cur - (atr * 0.3), ctx.ma5());
            secondaryEntry = Math.max(primaryEntry - (atr * 0.5), (ctx.ma5() + ctx.ma20()) / 2.0);
            stopLoss       = Math.min(cur - (atr * 2.0), secondaryEntry - (atr * 0.3));
            trailingStop   = Math.max(ctx.ma5(), cur - (atr * 1.5));
            primaryTarget  = !upResistances.isEmpty() ? Math.max(upResistances.get(0), cur + (atr * 0.8)) : cur + (atr * 1.0);
            secondaryTarget = primaryTarget + (atr * 1.5);

        } else if (phase == MarketPhase.ENTRY_READY || phase == MarketPhase.SETUP) {
            primaryEntry   = Math.max(cur - (atr * 0.5), ctx.ma20());
            secondaryEntry = Math.max(primaryEntry - (atr * 0.8), ctx.ma20() - (atr * 0.5));
            stopLoss       = Math.min(cur - (atr * 2.5), secondaryEntry - (atr * 0.5));
            trailingStop   = Math.max(ctx.ma20(), cur - (atr * 1.5));
            primaryTarget  = !upResistances.isEmpty() ? Math.max(upResistances.get(0), cur + (atr * 0.8)) : cur + (atr * 1.0);
            secondaryTarget = primaryTarget + (atr * 1.5);

        } else {
            // 하락 국면 (DANGER, COOLDOWN, RECOVERY_WATCH, WATCH)
            primaryEntry   = ctx.lower();
            secondaryEntry = ctx.lower() - (atr * 0.5);
            stopLoss       = secondaryEntry - (atr * 1.0);
            trailingStop   = Math.max(ctx.yesterdayPrice(), stopLoss);
            primaryTarget  = !upResistances.isEmpty() ? upResistances.get(0) : ctx.ma20();
            secondaryTarget = primaryTarget + atr;
        }

        double riskPerShare = primaryEntry - stopLoss;
        double recWeight = riskPerShare > 0 ? Math.min((2.0 / ((riskPerShare / primaryEntry) * 100)) * 100, 100.0) : 0;

        return new PricingResult(primaryEntry, secondaryEntry, stopLoss, primaryTarget, secondaryTarget, trailingStop, riskPerShare, recWeight);
    }

    // ── 행동강령 렌더링 ──
    private String generateVerdict(MarketContext ctx, MarketPhase phase, PricingResult pricing) {
        String sizingText = String.format("<br>💡 권장 진입비중: 총 자산의 %.0f%% 이내", pricing.recWeight);
        String trailingStopText = String.format("<br>🚨 하드스탑(%.0f) 이탈 시 전량 손절 / 트레일링스탑(%.0f) 이탈 시 절반 익절", pricing.stopLoss, pricing.trailingStop);

        switch (phase) {
            case DANGER_ZONE:
                return "🚨[추세 붕괴] 역배열 및 하방 압력 지속 국면<br>· 공통: 떨어지는 칼날 예측 매수 절대 금지";
            case RECOVERY_WATCH:
                return "🌱[반등 초기] 낙폭 과대 후 바닥 다지기 진행 중<br>· 미보유자: 보수적 관망 권장 (공격적 투자자 한정 입질 매수)";
            case WATCH:
                return "⚠️[방향성 탐색] 추세 미확립 및 횡보 국면<br>· 공통: 확실한 20일선 기울기 우상향 전환 대기";
            case SETUP:
                return "🌀[브레이크 대기] 정배열 진입 후 음봉 눌림목 진행 중<br>· 미보유자: 하락세 멈춤(양봉) 확정 시 진입 대기";
            case ENTRY_READY:
                return "🎯[안전 진입] 정배열 + 거래량 유입 브레이크 확인<br>"
                        + String.format("· 미보유자: 1차(%.0f) 타점에서 분할 매수 실행 %s %s", pricing.primaryEntry, trailingStopText, sizingText);
            case STRONG_HOLD:
                return "🔥[초강세 추세 지속] 불장 탑승 구간<br>"
                        + String.format("· 미보유자: 5일선 부근 1차(%.0f) 적극 편승 %s %s", pricing.primaryEntry, trailingStopText, sizingText);
            case WARNING_ZONE:
                return "⚠️[과열 경고] 단기 과열 및 모멘텀 둔화 포착<br>· 보유자: 신규 진입을 자제하고 익절가를 타이트하게 상향 조정 요망";
            case SCALE_OUT:
                return "💰[수익 실현] 1차 목표가 및 저항선 도달에 따른 오버슈팅<br>· 보유자: 물량의 50% 분할 익절 후 잔여분 추세 추종 대응";
            case COOLDOWN:
                return "🧊[매매 정지] 대량 거래 동반 지지선 이탈 및 휩소 발생<br>· 공통: 심리 회복 및 변동성 축소를 위해 24시간 뇌동 매매 강제 차단";
            default:
                return "💬 분석 대기 중";
        }
    }

    private record PricingResult(double primaryEntry, double secondaryEntry, double stopLoss, double primaryTarget, double secondaryTarget, double trailingStop, double riskPerShare, double recWeight) {}
}