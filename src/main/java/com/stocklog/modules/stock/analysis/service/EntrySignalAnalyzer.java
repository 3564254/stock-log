package com.stocklog.modules.stock.analysis.service;

import com.stocklog.modules.stock.analysis.domain.signal.vo.StockPhase;
import com.stocklog.modules.stock.analysis.domain.signal.SignalResultBuilder;
import com.stocklog.modules.stock.analysis.domain.signal.rule.SignalRule;
import com.stocklog.modules.stock.analysis.domain.signal.vo.*;
import com.stocklog.modules.stock.chart.dto.StockChartDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Slf4j
public class EntrySignalAnalyzer {

    private final List<SignalRule> signalRules;
    private static final double STRONG_HOLD_MAX_ATR   = 1.5;
    private static final double ENTRY_SETUP_MAX_ATR   = 2.0;
    private static final double WARNING_SCALEOUT_MAX_ATR = 2.5;
    private static final double RECOVERY_MAX_ATR      = 2.5;

    public EntrySignal analyze(List<StockChartDto> dailyCharts, List<StockChartDto> weeklyCharts) {

        MarketContext ctx = MarketContext.getMarketContext(dailyCharts, weeklyCharts);

        if (ctx == null) {
            return EntrySignal.builder()
                    .buySignals(List.of("데이터 부족 — 일봉 50개 이상 필요함"))
                    .sellSignals(List.of())
                    .build();
        }

        SignalResultBuilder builder = new SignalResultBuilder();
        for (SignalRule rule : signalRules) {
            rule.evaluate(ctx, builder);
        }

        StockPhase currentPhase = determinePhase(ctx);
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

    public StockPhase getPhase(MarketContext context) {
        return determinePhase(context);
    }

    public MiniSignalDto analyzeMini(List<StockChartDto> dailyCharts) {
        MarketContext ctx = MarketContext.getMarketContext(dailyCharts, List.of());
        if (ctx == null) return null;

        StockPhase currentPhase = determinePhase(ctx);
        PricingResult pricing = calculateTargets(ctx, currentPhase);
        String phaseName = getShortPhaseName(currentPhase);

        return new MiniSignalDto(
                currentPhase,
                phaseName,
                pricing.primaryEntry(),
                pricing.stopLoss()
        );
    }

    private String getShortPhaseName(StockPhase phase) {
        return switch (phase) {
            case DANGER_ZONE -> "하락 지속";
            case RECOVERY_WATCH -> "반등 초기";
            case WATCH -> "방향 탐색";
            case SETUP -> "단기 조정";
            case ENTRY_READY -> "진입 타이밍";
            case STRONG_HOLD -> "강세 지속";
            case WARNING_ZONE -> "과열 주의";
            case SCALE_OUT -> "수익 실현";
            case COOLDOWN -> "매매 휴식";
        };
    }

    // 종가 데이터 배열을 분석하여 원/달러(한국/미국) 시장 여부를 동적으로 판별
    private boolean isUsMarket(MarketContext ctx) {
        if (ctx.closes() == null || ctx.closes().isEmpty()) return false;

        // 시간복잡도 최적화 (O(N) -> O(1)): 최근 10개 데이터만 확인
        int size = ctx.closes().size();
        int startIdx = Math.max(0, size - 10);
        for (int i = startIdx; i < size; i++) {
            Double c = ctx.closes().get(i);
            if (c != null && c != Math.floor(c)) return true; // 소수점이 있으면 미국 주식으로 판별
        }
        return ctx.currentPrice() < 5000.0; // 소수점이 없더라도 가격이 작으면 미국 주식으로 판별
    }

    // 원/달러 맞춤형 포맷팅 헬퍼
    private String fmt(double val, boolean isUs) {
        return isUs ? String.format("$%,.2f", val) : String.format("%,.0f원", val);
    }

    public StockPhase determinePhase(MarketContext ctx) {
        int lastIdx = ctx.lastIdx();

        // ==========================================
        // 1. 프라이스 액션 기초 데이터 산출 (캔들 꼬리 분석용)
        // ==========================================
        // 안전한 리스트 접근 (데이터가 없으면 현재가로 대체)
        double currentHigh = (ctx.highs() != null && ctx.highs().size() > lastIdx) ? ctx.highs().get(lastIdx) : ctx.currentPrice();
        double currentLow = (ctx.lows() != null && ctx.lows().size() > lastIdx) ? ctx.lows().get(lastIdx) : ctx.currentPrice();

        // 시가 데이터가 Record에 없으므로 전일 종가를 시가 대용으로 사용하여 몸통 계산
        double bodyTop = Math.max(ctx.currentPrice(), ctx.yesterdayPrice());
        double bodyBottom = Math.min(ctx.currentPrice(), ctx.yesterdayPrice());
        double candleLength = currentHigh - currentLow;

        // 윗꼬리/밑꼬리 비율 (0.0 ~ 1.0)
        double upperWickRatio = candleLength > 0 ? (currentHigh - bodyTop) / candleLength : 0;
        double lowerWickRatio = candleLength > 0 ? (bodyBottom - currentLow) / candleLength : 0;

        boolean isBullishCandle = ctx.currentPrice() > ctx.yesterdayPrice();

        // 최근 20봉 고점 산출 (돌파 기준점)
        double recentHigh = Double.MIN_VALUE;
        int lookback = Math.min(20, lastIdx);
        if (lookback > 0 && ctx.highs() != null) {
            for (int i = lastIdx - lookback; i < lastIdx; i++) {
                recentHigh = Math.max(recentHigh, ctx.highs().get(i));
            }
        } else {
            recentHigh = Double.MAX_VALUE;
        }

        // ==========================================
        // 2. 주요 시그널 플래그 정의
        // ==========================================
        // 거래량 급증 & 갭
        boolean isVolumeSurge = ctx.projectedVolume() > ctx.volumeMa5() * 1.5;
        boolean isVolumeClimax = ctx.projectedVolume() > ctx.volumeMa5() * 3.0;
        boolean isGapUp = ctx.currentPrice() > ctx.yesterdayPrice() * 1.03;
        boolean isGapDown = ctx.currentPrice() < ctx.yesterdayPrice() * 0.97;

        // 매물대 및 기관 평단 돌파 여부 (새로 추가된 필드 활용)
        boolean breakoutPOC = ctx.yesterdayPrice() <= ctx.pocPrice() && ctx.currentPrice() > ctx.pocPrice();
        boolean breakoutVWAP = ctx.yesterdayPrice() <= ctx.vwap20() && ctx.currentPrice() > ctx.vwap20();

        // 추세 상태
        boolean shortTermDowntrend = ctx.ma5() < ctx.ma20() && ctx.currentPrice() < ctx.ma20();
        boolean structuralDowntrend = shortTermDowntrend && ctx.ma20() < ctx.ma50();
        boolean isNearMa20 = Math.abs(ctx.distFromMa20()) < (ctx.atr() * 0.8);

        // 트랩(Trap) 및 가짜 신호 포착
        boolean isBullTrap = (currentHigh >= recentHigh || currentHigh >= ctx.upper())
                && (upperWickRatio > 0.5)
                && isVolumeSurge;

        boolean isBearTrap = (currentLow <= ctx.lower())
                && (lowerWickRatio > 0.5)
                && isVolumeSurge;

        boolean isDeadCatBounce = structuralDowntrend
                && isBullishCandle
                && ctx.projectedVolume() < ctx.volumeMa5() * 0.7;


        // ==========================================
        // 3. 🚀 페이즈(Phase) 판정 우선순위 로직
        // ==========================================

        // 🚨 [0순위] 불트랩 방어 (가짜 돌파 후 윗꼬리 투매)
        if (isBullTrap) {
            return StockPhase.WARNING_ZONE;
        }

        // 🟢 [1순위] 베어트랩 및 투매 절정 (바닥 잡기)
        if (isBearTrap && ctx.rsi() < 40) {
            return StockPhase.ENTRY_READY; // 밴드 하단 이탈 후 대량거래 동반 말아올림 (찐바닥)
        }

        boolean isPanicSelling = ctx.currentPrice() < ctx.lower() && ctx.rsi() < 30;
        if (isPanicSelling) {
            if (isVolumeClimax && isBullishCandle) {
                return StockPhase.RECOVERY_WATCH; // 거래량 터진 양봉 발생 시 V자 반등 대기
            }
            return StockPhase.COOLDOWN; // 단순 패닉셀 지속
        }

        // 🚨 [2순위] 치명적 지지선 이탈 (Gap Down)
        if (isGapDown && structuralDowntrend) {
            return StockPhase.COOLDOWN;
        }

        // 🔥 [3순위] 강력한 저항선 돌파 (POC, VWAP, 전고점)
        boolean isAggressiveBreakout = (breakoutPOC || breakoutVWAP || ctx.currentPrice() > recentHigh)
                && isVolumeSurge
                && isBullishCandle;

        if (isAggressiveBreakout) {
            if (isGapUp || ctx.isStrongTrend()) {
                return StockPhase.STRONG_HOLD; // 저항선을 갭으로 넘거나, 이미 초강세장인 경우
            }
            return StockPhase.ENTRY_READY; // 확실한 돌파 타점
        }

        // ⚠️ [4순위] 과열 구간 및 모멘텀 둔화 (Scale Out)
        if (ctx.rsi() >= 70 || ctx.currentPrice() >= ctx.upper()) {
            // 고점이 낮아지거나(LH), MACD가 꺾이거나, 매도 거래량이 터지면 익절 시작
            if (!ctx.macd().isHistogramRising() || ctx.isVolBearish() || ctx.isLH()) {
                return StockPhase.SCALE_OUT;
            }
            return StockPhase.WARNING_ZONE; // 아직 꺾이진 않은 과열 상태
        }

        // 📉 [5순위] 하락장 속 반등 시그널 판단
        if (structuralDowntrend || shortTermDowntrend) {
            if (isDeadCatBounce) {
                return StockPhase.DANGER_ZONE; // 거래량 없는 가짜 반등은 즉시 무시
            }

            int recoverySignals = 0;
            if (ctx.currentPrice() > ctx.ma5()) recoverySignals++;
            if (ctx.macd().isGoldenCross() || ctx.macd().isHistogramRising()) recoverySignals++;
            if (ctx.dailyPivot() != null && ctx.currentPrice() > ctx.dailyPivot().p()) recoverySignals++;
            if (ctx.isVolBullish()) recoverySignals++;
            if (ctx.isHL()) recoverySignals++; // 저점 상승(Higher Low) 확인

            if (recoverySignals >= 3) {
                return StockPhase.RECOVERY_WATCH;
            }
            return StockPhase.DANGER_ZONE; // 조건 미달 시 하락장 유지
        }

        // 🎯 [6순위] 돌파 전 폭발 임박 (Squeeze) 및 눌림목 (Pullback)
        if (ctx.isSqueeze()) {
            return StockPhase.SETUP; // 볼린저 밴드 극단적 수축 -> 위아래 방향성 결정 주시
        }

        boolean reclaimedMa5 = ctx.yesterdayPrice() <= ctx.ma5Yesterday() && ctx.currentPrice() > ctx.ma5();
        boolean isPullbackEntry = isNearMa20 && reclaimedMa5 && ctx.isMa20Rising() && isBullishCandle && ctx.isVolBullish();

        if (isPullbackEntry) {
            return StockPhase.ENTRY_READY; // 20일선 눌림 후 5일선 회복 (교과서적 눌림목)
        }

        if (ctx.isUptrend() && isNearMa20 && ctx.currentPrice() < ctx.ma5()) {
            return StockPhase.SETUP; // 아직 반등 양봉은 안 나왔으나 20일선 근처에서 대기 중
        }

        // 💎 [7순위] 추세 지속 판단
        if (ctx.isMacroUptrend() || ctx.isStrongTrend()) {
            // ADX가 25 이상이며 20일선 위에 있으면 추세 홀딩
            if (ctx.adx() >= 25 && ctx.isMa20Rising() && ctx.currentPrice() > ctx.ma20()) {
                return StockPhase.STRONG_HOLD;
            }
        }

        // 💤 [8순위] 기타 횡보 및 관망
        if (ctx.isSideways() || ctx.isWeakTrend()) {
            return StockPhase.WATCH;
        }

        return StockPhase.WATCH;
    }

    private void addPhaseSpecificSignals(StockPhase phase, MarketContext ctx, SignalResultBuilder builder) {
        if (ctx.isUptrend() && phase != StockPhase.DANGER_ZONE && phase != StockPhase.WATCH) {
            builder.addBuySignal("✅ [추세 확립] 정배열 및 20일선 우상향 기울기 확보");
        }

        switch (phase) {
            case ENTRY_READY -> builder.addBuySignal("🛡️ [타점 확정] 저항 돌파 또는 단기 지지선 회복 확인");
            case STRONG_HOLD -> builder.addBuySignal("🚀 [추세 지속] 강한 추세가 지속되는 정배열 상승 구간");
            case RECOVERY_WATCH -> {
                if (ctx.macd().isGoldenCross()) {
                    builder.addBuySignal("🌱 [바닥 확인] 과매도권 탈출 및 MACD 모멘텀 호전");
                } else {
                    builder.addBuySignal("🌱 [바닥 확인] 주요 지지선 방어 및 단기 회복세 포착");
                }
            }
            case WARNING_ZONE -> builder.addSellSignal("⚠️ [과열 경고] RSI 70 상회 및 모멘텀 상승세 둔화");
            case SCALE_OUT -> builder.addSellSignal("💰 [수익 관리] 강한 상승으로 주요 저항 진입 및 탄력 둔화 (분할 익절 권장)");
            case COOLDOWN -> builder.addSellSignal("🧊 [투매 발생] 주요 지지선 이탈 및 패닉 셀링 동반");
        }
    }

    private PricingResult calculateTargets(MarketContext ctx, StockPhase phase) {
        double atr = ctx.atr();
        double cur = ctx.currentPrice();

        // 0.2 ATR 이하의 너무 얕은 저항은 무시하여 불필요한 손익비 저하 방지 (0.5 ATR로 상향)
        List<Double> upResistances = Stream.of(
                        ctx.dailyPivot().r1(), ctx.dailyPivot().r2(), ctx.dailyPivot().r3(),
                        ctx.upper(), ctx.pocPrice(), ctx.vwap20()
                )
                .filter(p -> p != null && !Double.isNaN(p) && p > (cur + (atr * 0.5)))
                .sorted()
                .toList();

        List<Double> downSupports = Stream.of(
                        ctx.dailyPivot().s1(), ctx.dailyPivot().s2(), ctx.dailyPivot().s3(),
                        ctx.lower(), ctx.ma20(), ctx.ma50(), ctx.pocPrice(), ctx.vwap20()
                )
                .filter(p -> p != null && !Double.isNaN(p) && p < cur)
                .sorted((a, b) -> Double.compare(b, a))
                .toList();

        double primaryEntry, secondaryEntry, stopLoss, primaryTarget, secondaryTarget, trailingStop;
        double bestSupport = !downSupports.isEmpty() ? downSupports.get(0) : cur - (atr * 0.5);

        if (phase == StockPhase.STRONG_HOLD) {
            primaryEntry   = Math.max(bestSupport, cur - (atr * STRONG_HOLD_MAX_ATR));
            secondaryEntry = Math.max(primaryEntry - (atr * 0.8), ctx.ma20());
            stopLoss       = secondaryEntry - (atr * 0.5);
            trailingStop   = Math.max(primaryEntry, cur - (atr * 1.0));
            primaryTarget  = !upResistances.isEmpty() ? upResistances.get(0) : cur + (atr * 1.5);
            secondaryTarget = primaryTarget + (atr * 1.0);

        } else if (phase == StockPhase.ENTRY_READY || phase == StockPhase.SETUP) {
            primaryEntry   = Math.max(bestSupport, cur - (atr * ENTRY_SETUP_MAX_ATR));

            double nextSupport = downSupports.size() > 1 ? downSupports.get(1) : primaryEntry - (atr * 0.8);
            secondaryEntry = Math.min(nextSupport, primaryEntry - (atr * 0.5));
            secondaryEntry = Math.max(secondaryEntry, cur - (atr * (ENTRY_SETUP_MAX_ATR + 1.0)));

            stopLoss       = secondaryEntry - (atr * 0.5);
            trailingStop   = Math.max(secondaryEntry, cur - (atr * 1.0));
            primaryTarget  = !upResistances.isEmpty() ? upResistances.get(0) : cur + (atr * 1.2);
            secondaryTarget = primaryTarget + (atr * 1.0);

        } else if (phase == StockPhase.WARNING_ZONE || phase == StockPhase.SCALE_OUT) {
            primaryEntry   = Math.max(Math.min(cur - (atr * 1.0), ctx.ma20()), cur - (atr * WARNING_SCALEOUT_MAX_ATR));
            secondaryEntry = primaryEntry - (atr * 1.0);
            stopLoss       = (ctx.ma50() > 0 ? ctx.ma50() : secondaryEntry) - (atr * 0.5);
            trailingStop   = Math.max(ctx.ma20(), cur - (atr * 0.8));
            primaryTarget  = !upResistances.isEmpty() ? upResistances.get(0) : cur + (atr * 0.8);
            secondaryTarget = primaryTarget + (atr * 0.8);

        } else {
            double recoverySupport = !downSupports.isEmpty() ? downSupports.get(0) : cur - (atr * RECOVERY_MAX_ATR);
            primaryEntry   = Math.max(recoverySupport, cur - (atr * RECOVERY_MAX_ATR));
            secondaryEntry = primaryEntry - (atr * 0.8);

            double structuralLow = ctx.lower() > 0 ? ctx.lower() : secondaryEntry - (atr * 1.0);
            stopLoss = Math.min(secondaryEntry - (atr * 0.5), structuralLow - (atr * 0.3));

            trailingStop   = stopLoss;

            double riskEstimate = primaryEntry - stopLoss;
            double minTargetByRisk = primaryEntry + (riskEstimate * 1.3); // 최소 RR 1.3 확보
            primaryTarget = !upResistances.isEmpty()
                    ? Math.max(upResistances.get(0), minTargetByRisk)
                    : minTargetByRisk;
            secondaryTarget = primaryTarget + (atr * 1.0);
        }

        // 가격 순서 역전 방지 하드 리미트 적용
        primaryEntry = Math.min(primaryEntry, cur);
        secondaryEntry = Math.min(secondaryEntry, primaryEntry - (atr * 0.1));
        stopLoss = Math.min(stopLoss, secondaryEntry - (atr * 0.1));

        // 최소 손절폭 보장 (Whipsaw 방어)
        double minStopDistance = Math.max(cur * 0.015, atr * 0.8);
        if ((primaryEntry - stopLoss) < minStopDistance) {
            stopLoss = primaryEntry - minStopDistance;
        }

        double riskPerShare = primaryEntry - stopLoss;
        double reward = primaryTarget - primaryEntry;
        double riskRewardRatio = riskPerShare > 0 ? (reward / riskPerShare) : 0;

        double volatilityRatio = (atr / cur) * 100;
        double recWeight = riskPerShare > 0 ? Math.min((2.0 / ((riskPerShare / primaryEntry) * 100)) * 100, 100.0) : 0;

        if (volatilityRatio > 7.0) recWeight = recWeight * 0.5;

        double maxWeight = switch (phase) {
            case DANGER_ZONE, COOLDOWN, WATCH, WARNING_ZONE, SCALE_OUT -> 0.0;
            case RECOVERY_WATCH -> 20.0;
            case SETUP -> 40.0;
            case ENTRY_READY -> 60.0;
            case STRONG_HOLD -> 100.0;
        };

        recWeight = Math.min(recWeight, maxWeight);

        if (riskRewardRatio < 1.2) {
            recWeight = 0;
        }

        return new PricingResult(primaryEntry, secondaryEntry, stopLoss, primaryTarget, secondaryTarget, trailingStop, riskPerShare, recWeight, riskRewardRatio, volatilityRatio);
    }

    private record PricingResult(double primaryEntry, double secondaryEntry, double stopLoss, double primaryTarget, double secondaryTarget, double trailingStop, double riskPerShare, double recWeight, double riskRewardRatio, double volatilityRatio) {}

    // 행동강령 렌더링
    private String generateVerdict(
            MarketContext ctx,
            StockPhase phase,
            PricingResult pricing
    ) {
        StringBuilder sb = new StringBuilder();

        boolean isUs = isUsMarket(ctx);

        double rr = pricing.riskRewardRatio();
        boolean isPoorRR = (rr > 0 && rr < 0.8);

        double currentPrice = ctx.currentPrice();
        double entryPrice = pricing.primaryEntry();
        double stopLoss = pricing.stopLoss();
        double trailingStop = pricing.trailingStop();

        boolean hasValidEntry = Double.isFinite(entryPrice) && entryPrice > 0;
        boolean hasValidStop = Double.isFinite(stopLoss) && stopLoss > 0;
        boolean hasValidTrailingStop = Double.isFinite(trailingStop) && trailingStop > 0;

        // ATR 기반 이격도 및 리스크 퍼센트 계산 (개선 로직)
        double atrDistance = (hasValidEntry && ctx.atr() > 0)
                ? (currentPrice - entryPrice) / ctx.atr()
                : Double.NaN;

        double currentRiskPercent = (hasValidStop && currentPrice > 0)
                ? ((currentPrice - stopLoss) / currentPrice) * 100
                : 0.0;
        String riskText = String.format("%.1f%%", currentRiskPercent);

        String entryText = hasValidEntry ? fmt(entryPrice, isUs) : "산출 대기";
        String stopText = hasValidStop ? fmt(stopLoss, isUs) : "산출 대기";
        String trailingStopText = hasValidTrailingStop ? fmt(trailingStop, isUs) : "산출 대기";

        String colorRed = "var(--toss-red)";
        String colorBlue = "var(--toss-blue)";

        boolean volumeBullish = ctx.isVolBullish();
        boolean volumeBearish = ctx.isVolBearish();
        // 💡 렌더링 측면에서도 Projected Volume 활용
        boolean isVolumeClimax = ctx.projectedVolume() > ctx.volumeMa5() * 3.0;

        boolean oversold = ctx.rsi() < 30;
        boolean overbought = ctx.rsi() > 70;

        boolean strongTrend = ctx.adx() > 30;
        boolean weakTrend = ctx.adx() < 20;

        boolean upperBandBreak = ctx.percentB() > 1.0;
        boolean macdGoldenCross = ctx.macd() != null && ctx.macd().isGoldenCross();
        boolean pivotRecovered = ctx.dailyPivot() != null && currentPrice > ctx.dailyPivot().p();
        boolean isGapUp = ctx.currentPrice() > ctx.yesterdayPrice() * 1.03;

        sb.append("""
        <div style='font-size:16px; font-weight:800; color:var(--text-dark); margin-bottom:8px;'>
        """);
        sb.append(getPhaseFactBriefing(phase, ctx));
        sb.append("</div>");

        sb.append("""
        <div style='font-size:14.5px; line-height:1.6; color:var(--text-secondary); margin-bottom:20px;'>
        """);

        switch (phase) {
            case DANGER_ZONE -> {
                sb.append("투심이 완전히 꺾이며 주요 지지선이 모두 무너진 <b>전형적인 역배열 폭포수 구간</b>입니다.");
                if (volumeBearish) {
                    sb.append("<br>📉 특히 <b style='color:").append(colorBlue).append("'>매도 거래량이 점증</b>하고 있어 바닥을 섣불리 예측하기 힘든 상태입니다.");
                }
                if (oversold) {
                    sb.append("<br>현재 RSI가 <b>").append(String.format("%.1f", ctx.rsi())).append("</b>로 과매도권이긴 하나, 하락장에서는 과매도가 일상적으로 발생하므로 맹신하면 안 됩니다.");
                }
            }
            case COOLDOWN -> {
                sb.append("급격한 가격 파괴 이후 시장 참여자들이 충격에서 벗어나지 못한 <b>변동성 진정(쿨다운) 대기 구간</b>입니다.");

                if (isVolumeClimax) {
                    sb.append("<br>⚠️ <b>투매 절정(Selling Climax):</b> 평소 대비 압도적인 거래량이 터지며 패닉셀이 쏟아졌습니다. <b>'떨어지는 칼날'</b>을 섣불리 잡기보다 바닥이 다져지는지 지켜봐야 합니다.");
                } else {
                    sb.append("<br>📉 <b>매도세 지속:</b> 아직 하락 브레이크가 걸리지 않았습니다. 잔여 매도 물량이 소화되며 십자도지(Doji) 같은 반전 캔들이 출현하는지 확인해야 합니다.");
                }

                // 💡 구체적인 행동 지침 추가
                sb.append("<br>💡 <b>대응 전략:</b> 의미 있는 반등 시그널이나 이중 바닥(쌍바닥)이 확인되기 전까지는 <b>신규 매수를 철저히 배제하고 관망(Wait & See)</b>하는 것이 안전합니다.");
            }
            case WARNING_ZONE -> {
                sb.append("단기 급등으로 인해 <b>언제든 차익 실현 매물이 쏟아질 수 있는 경계 및 과열 구간</b>입니다.");

                if (upperBandBreak) {
                    sb.append("<br>🔥 <b>밴드 이탈:</b> 볼린저 밴드 상단을 이탈했습니다. 강력한 추세를 뜻하기도 하지만, 곧바로 밴드 내부로 회귀하려는 단기 조정 압력을 강하게 받게 됩니다.");
                }
                if (overbought) {
                    sb.append("<br>📈 <b>RSI 과매수:</b> 단기 모멘텀 지표인 RSI가 <b>").append(String.format("%.1f", ctx.rsi())).append("</b>에 달해 기술적 고점 징후를 뚜렷하게 보이고 있습니다.");
                }

                // 💡 보유자/미보유자를 나눈 구체적 가이드
                sb.append("<br>💡 <b>대응 전략:</b> 현재는 철저한 <b>'보유자의 영역'</b>입니다. FOMO(소외 불안감)에 휩쓸린 <b>신규 추격 매수는 절대 지양</b>하시고, 보유 중이라면 익절가(Trailing Stop)를 바짝 올려 잡으세요.");
            }
            case SCALE_OUT -> {
                sb.append("상승 엔진은 아직 꺼지지 않았으나, <b>상승 탄력이 눈에 띄게 둔화되기 시작한 부분 익절(Scale-Out) 대응 구간</b>입니다.");

                // 💡 1. 탄력 둔화의 구체적인 '데이터 근거'를 사용자에게 제시
                boolean isRsiOverbought = ctx.rsi() >= 70;
                boolean isMacdDeclining = ctx.macd() != null && !ctx.macd().isHistogramRising();
                boolean isBearishVol = ctx.isVolBearish();

                if (isRsiOverbought || isMacdDeclining || isBearishVol) {
                    sb.append("<br>⚠️ <b>모멘텀 둔화 신호:</b> ");
                    if (isRsiOverbought) sb.append("RSI 단기 과열권 진입");
                    else if (isBearishVol) sb.append("차익 실현성 대량 매도 거래량 출회");
                    else sb.append("MACD 상승 모멘텀 감소");
                    sb.append(" 현상이 포착되었습니다.");
                }

                // 💡 2. '분할 매도'라는 구체적인 행동 지침 강조
                sb.append("<br>💡 단기 고점을 찍고 밀리는 패턴에 대비해, <b>보유 물량의 절반(또는 일부)을 매도하여 수익을 확실히 챙기고</b> 리스크를 줄이는 전략을 강력히 권장합니다.");

                // 💡 3. 강한 추세일 때의 '남은 물량' 핸들링 가이드
                if (strongTrend) {
                    sb.append("<br>다만, 추세 강도(ADX: <b>").append(String.format("%.1f", ctx.adx()))
                            .append("</b>)가 여전히 높아 단번에 폭락하기보다는 <b>쌍봉(Double Top)을 형성하거나 횡보 후 방향을 재탐색</b>할 가능성이 높습니다. 챙겨둔 수익을 담보로 남은 물량만 20일선 지지 여부를 보며 여유 있게 추적하세요.");
                }
            }
            case STRONG_HOLD -> {
                sb.append("매도 물량을 매수세가 가볍게 씹어먹으며 <b>주가가 우상향하는 쾌속 질주 구간</b>입니다.");
                // 1. 갭 상승 맥락 구체화
                if (isGapUp) {
                    sb.append("<br>🚀 <b>강력한 갭상승 발생:</b> 매물대를 갭으로 뛰어넘으며 상방 압력이 강하게 분출되었습니다.");
                }
                // 2. ADX 수치 표현 자연스럽게 다듬기
                if (strongTrend) {
                    sb.append("<br>추세 강도(ADX)가 <b>").append(String.format("%.1f", ctx.adx()))
                            .append("</b>로 강력한 상승 모멘텀이 유지되고 있어, 기세가 쉽게 꺾이지 않을 것으로 분석됩니다.");
                }
                // 3. 💡 실전 행동 요령(트레일링 스탑) 및 단기 이격 경고 추가
                if (ctx.distFromMa20() > ctx.atr() * 2.0) {
                    // 단기 이격이 크게 벌어진 경우
                    sb.append("<br>💡 단기 급등으로 이격이 벌어져 있으므로, 신규 추격 매수보다는 <b>5일선(").append(fmt(ctx.ma5(), isUs)).append(")을 트레일링 스탑 기준선</b>으로 삼아 수익을 극대화하는 전략이 유리합니다.");
                } else {
                    // 안정적으로 추세를 타는 중인 경우
                    sb.append("<br>💡 5일선 및 20일선(").append(fmt(ctx.ma20(), isUs)).append(")을 훼손하지 않는 한 물량을 뺏기지 말고 <b>홀딩을 유지</b>하는 구간입니다.");
                }
            }
            case ENTRY_READY -> {
                sb.append("무거운 저항선을 뚫어내거나 핵심 지지선을 딛고 일어서며 <b>상승 랠리의 1차 관문을 통과</b>했습니다.");
                if (volumeBullish) {
                    sb.append("<br>💰 의미 있는 <b>매수 거래량 유입</b>이 반등에 신뢰를 더해주고 있습니다.");
                }
                if (macdGoldenCross) {
                    sb.append("<br>차트의 심장인 MACD가 골든크로스를 그리며 상승 모멘텀에 점화가 시작되었습니다.");
                }
            }
            case SETUP -> {
                sb.append("거대한 상승 추세라는 파도 위에서 <b>잠시 숨을 고르며 다음 도약을 준비하는 아름다운 눌림목 구간</b>입니다.");
                if (ctx.isVolumeDryingUp()) {
                    // 찐 매도세 고갈 (거래량 급감)
                    sb.append("<br>조정을 받는 동안 거래량이 말라붙어(매도세 고갈), 기존 세력이 물량을 쥐고 있는 건전한 조정일 확률이 높습니다.");
                } else if (volumeBearish) {
                    // 대량 매도 거래량 발생
                    sb.append("<br>다만 음봉에 강한 거래량이 발생해 생명선인 20일선(").append(fmt(ctx.ma20(), isUs)).append(") 지지 여부를 끝까지 확인해야 합니다.");
                } else {
                    // 평이한 거래량 수준의 일반적인 조정
                    sb.append("<br>일반적인 거래량 범위 내에서 숨고르기가 진행 중이며, 20일선 지지 후 재차 반등하는지 주시할 필요가 있습니다.");
                }
            }
            case RECOVERY_WATCH -> {
                sb.append("지루한 하락세의 끝자락에서 <b>하락 브레이크가 걸리며 바닥을 다지고 있는 초기 회복 국면</b>입니다.");

                // 💡 1. else if를 분리하여 복수의 긍정 시그널을 모두 출력하도록 수정
                if (isVolumeClimax && !volumeBearish) {
                    sb.append("<br>🔥 <b style='color:").append(colorRed).append("'>스마트 머니(Smart Money) 유입 징후:</b> 투매 절정 이후 쏟아지는 물량을 누군가 대거 쓸어 담은 강력한 바닥 반전 징후가 포착되었습니다.");
                }

                if (pivotRecovered) {
                    sb.append("<br>📈 단기 주요 지지/저항선인 일일 피벗 중심선(<b>").append(fmt(ctx.dailyPivot().p(), isUs)).append("</b>) 위로 머리를 내밀며 투자 심리가 빠르게 호전되고 있습니다.");
                }

                // 💡 2. '가짜 반등'을 조심하라는 당부와 구체적 액션 플랜 추가
                sb.append("<br>💡 <b>대응 전략:</b> 아직 완벽한 상승 추세로의 전환(Turnaround)이 확인된 것은 아닙니다. 즉각적인 큰 비중 매수보다는 <b>관심 종목 최상단에 등록</b>해두고, 20일선 안착이나 이중 바닥(쌍바닥)이 완성되는지를 확인하며 <b>소액으로 분할 매수(Scale-in)</b>를 준비하세요.");
            }
            case WATCH -> {
                sb.append("방향성을 결정짓는 뚜렷한 매수/매도 주체 없이 <b>치열한 눈치싸움이 진행 중인 관망 국면</b>입니다.");

                // 💡 1. Squeeze와 weakTrend를 분리하여 모든 팩트 전달
                if (ctx.isSqueeze()) {
                    sb.append("<br>🌪️ <b>변동성 폭발 임박(Squeeze):</b> 볼린저 밴드가 극도로 수축된 폭풍 전야 상태입니다. 조만간 위든 아래든 강한 추세가 터질 확률이 매우 높습니다.");
                }

                if (weakTrend) {
                    sb.append("<br>💤 <b>소외 및 추세 부재:</b> 현재 추세 강도(ADX)가 <b>").append(String.format("%.1f", ctx.adx())).append("</b>로 바닥권입니다. 시장의 관심이 멀어져 있어 섣불리 진입하면 자금이 장기간 묶일 수 있습니다.");
                }

                // 💡 2. 횡보장 특성에 맞는 20일선 위치 해석
                if (currentPrice < ctx.ma20()) {
                    sb.append("<br>📉 <b>하방 압력 우세:</b> 주가가 생명선인 20일선(").append(fmt(ctx.ma20(), isUs)).append(") 아래에 억눌려 있어, 박스권 하단을 깨고 내려갈 리스크를 항상 열어두어야 합니다.");
                } else {
                    sb.append("<br>⚠️ <b>가짜 신호(Fakeout) 주의:</b> 주가가 20일선(").append(fmt(ctx.ma20(), isUs)).append(") 위에서 버티고 있으나, 거래량이 동반되지 않아 언제든 다시 밑으로 빠지는 휩쏘일 가능성이 높습니다.");
                }

                // 💡 3. 기회비용을 살리기 위한 구체적 행동 지침 추가
                sb.append("<br>💡 <b>대응 전략:</b> 현재 자리에서 방향을 미리 예측하는 '홀짝 베팅'은 기회비용과 승률 면에서 매우 불리합니다. <b>현금을 보유한 채 관망</b>하시고, 대량 거래를 동반한 박스권 돌파나 주요 지지선 이탈이 확정될 때까지 기다리세요.");
            }
            default -> sb.append("현재 가격과 보조지표 데이터를 바탕으로 실시간 차트 구조를 분석하고 있습니다.");
        }
        sb.append("</div>");

        sb.append("</div>");

        sb.append("""
        <div style='background:white; padding:18px; border-radius:16px; border:1px solid #edf2f7; box-shadow:0 4px 12px rgba(0,0,0,0.02);'>
        """);

        switch (phase) {
            case DANGER_ZONE, COOLDOWN -> {
                sb.append("""
        <div style='font-size:15px; font-weight:800; color:var(--text-dark); margin-bottom:6px;'>
            🚨 관망 유지 (매수 엄금)
        </div>
        <div style='font-size:14px; color:var(--text-secondary); line-height:1.5;'>
        """);
                sb.append("현재 하방 압력이 매우 강합니다. 섣부른 물타기(평단가 낮추기)나 신규 진입을 절대 금합니다.<br>");
                sb.append("예상 지지 구간(<b>").append(entryText).append("</b>) 부근에서 거래량이 마르고 뚜렷한 반전 양봉이 발생할 때까지 현금을 보유하세요.");
                sb.append("</div>");
            }
            case WARNING_ZONE -> {
                sb.append("""
        <div style='font-size:15px; font-weight:800; color:var(--toss-red); margin-bottom:6px;'>
            🔥 신규 추격 매수 절대 금지
        </div>
        <div style='font-size:14px; color:var(--text-secondary); line-height:1.5;'>
        """);
                sb.append("단기 급등으로 인해 신규 진입 시 리스크가 너무 큰 과열 구간입니다.<br>");
                sb.append("기존 보유자는 수익을 지키는 데 집중하고, <b>").append(trailingStopText).append("</b> 이탈 시 기계적으로 익절하여 수익을 확정 지으세요.");
                sb.append("</div>");
            }
            case SCALE_OUT -> {
                sb.append("""
        <div style='font-size:15px; font-weight:800; color:var(--toss-blue); margin-bottom:6px;'>
            💰 기계적 분할 익절 실행
        </div>
        <div style='font-size:14px; color:var(--text-secondary); line-height:1.5;'>
        """);
                sb.append("상승 탄력이 눈에 띄게 둔화되고 있습니다. 보유 물량의 30~50%를 즉시 매도하여 수익을 챙기세요.<br>");
                if (hasValidTrailingStop) {
                    sb.append("남은 잔여 물량은 <b>").append(trailingStopText).append("</b> 이탈 시 전량 청산하여 리스크를 완벽히 차단하세요.");
                }
                sb.append("</div>");
            }

            case STRONG_HOLD -> {
                if (!hasValidEntry) {
                    appendPricingUnavailableMessage(sb);
                } else if (atrDistance > 1.5) {
                    // 💡 수정: STRONG_HOLD의 정체성에 맞게 '보유자 중심' 메시지로 톤앤매너 변경
                    sb.append("""
            <div style='font-size:15px; font-weight:800; color:var(--toss-red); margin-bottom:6px;'>
                🛡️ 보유자 수익 극대화 (신규 진입 주의)
            </div>
            <div style='font-size:14px; color:var(--text-secondary); line-height:1.5;'>
            """);
                    sb.append("기존 보유자들이 수익을 온전히 누리는 쾌속 질주 구간입니다. 다만 지금 신규 진입 시 손절폭이 <b>").append(riskText).append("</b>에 달해 위험합니다.<br>");
                    sb.append("신규 진입은 <b>").append(entryText).append("</b> 부근 눌림목까지 인내심을 갖고 기다리세요.");
                    sb.append("</div>");
                } else if (atrDistance >= -0.5) {
                    if (isPoorRR) {
                        // 💡 수정: 비중 축소에 대한 명확한 기준(절반 이하) 제시
                        sb.append("""
                <div style='font-size:15px; font-weight:800; color:var(--toss-orange); margin-bottom:6px;'>
                    ⚠️ 보수적 진입 (손익비 불리)
                </div>
                <div style='font-size:14px; color:var(--text-secondary); line-height:1.5;'>
                """);
                        sb.append("강한 추세가 유지 중이나 단기 저항이 가까워 기대 수익 대비 리스크(손절폭)가 큽니다.<br>");
                        sb.append("진입하더라도 <b>평소 투자 비중의 절반(50%) 이하</b>로 대폭 줄여서 접근하세요.");
                        sb.append("</div>");
                    } else {
                        sb.append("<div style='font-size:15px; font-weight:800; color:").append(colorRed).append("; margin-bottom:6px;'>🚀 추세 탑승 (분할 매수 유효)</div>");
                        sb.append("<div style='font-size:14px; color:var(--text-secondary); line-height:1.5;'>");
                        sb.append("강한 상승 추세 내 안착한 유효 타점입니다. 손절가 <b>").append(stopText).append("</b>를 엄수한다는 원칙하에 과감하게 진입을 실행하세요.");
                        sb.append("</div>");
                    }
                } else {
                    sb.append("""
            <div style='font-size:15px; font-weight:800; color:var(--text-dark); margin-bottom:6px;'>
                🟡 지지선 재확인 (신규 매수 보류)
            </div>
            <div style='font-size:14px; color:var(--text-secondary); line-height:1.5;'>
            """);
                    sb.append("주가가 예상 진입가(<b>").append(entryText).append("</b>)를 일시적으로 하회하고 있습니다.<br>");
                    sb.append("단순 저가 매수를 피하고, 종가 마감 시 지지선을 완벽히 회복해 내는지 먼저 확인하세요.");
                    if (hasValidTrailingStop) {
                        sb.append("<br>기존 보유자는 <b>").append(trailingStopText).append("</b> 이탈 시 기계적으로 비중을 줄여야 합니다.");
                    }
                    sb.append("</div>");
                }
            }
            case ENTRY_READY, SETUP -> {
                if (!hasValidEntry) {
                    appendPricingUnavailableMessage(sb);
                } else if (atrDistance > 1.0) {
                    sb.append("""
            <div style='font-size:15px; font-weight:800; color:var(--text-dark); margin-bottom:6px;'>
                🟠 매수 대기 (지정가 활용)
            </div>
            <div style='font-size:14px; color:var(--text-secondary); line-height:1.5;'>
            """);
                    sb.append("현재가 진입 시 감수해야 할 리스크(<b>").append(riskText).append("</b>)가 큽니다.<br>");
                    sb.append("시장가 추격 매수를 자제하고, <b>").append(entryText).append("</b> 부근에 지정가(Limit Order) 매수를 걸어두어 안전한 눌림목을 잡으세요.");
                    sb.append("</div>");
                } else if (atrDistance >= -0.5) {
                    if (isPoorRR) {
                        sb.append("""
                <div style='font-size:15px; font-weight:800; color:var(--toss-orange); margin-bottom:6px;'>
                    ⚠️ 진입 패스 (손익비 불량)
                </div>
                <div style='font-size:14px; color:var(--text-secondary); line-height:1.5;'>
                """);
                        sb.append("타점 자체는 유효하나, 기대 수익보다 손절 시 타격이 더 큰 비효율적인 자리입니다.<br>");
                        sb.append("가성비가 떨어지므로 굳이 리스크를 감수하기보다 이번 기회는 패스하는 것을 권장합니다.");
                        sb.append("</div>");
                    } else {
                        sb.append("<div style='font-size:15px; font-weight:800; color:").append(colorRed).append("; margin-bottom:6px;'>🎯 즉각 1차 분할 매수 실행</div>");
                        sb.append("<div style='font-size:14px; color:var(--text-secondary); line-height:1.5;'>");
                        sb.append("리스크 대비 기대 수익이 가장 높은 <b>최적의 매수 타점</b>입니다.<br>");
                        sb.append("예정된 총비중의 30~50%를 우선 진입하고, <b>").append(stopText).append("</b> 이탈 시 미련 없이 손절 대응하세요.");
                        sb.append("</div>");
                    }
                } else {
                    sb.append("""
            <div style='font-size:15px; font-weight:800; color:var(--toss-orange); margin-bottom:6px;'>
                🟡 지지 이탈 (종가 확인 필수)
            </div>
            <div style='font-size:14px; color:var(--text-secondary); line-height:1.5;'>
            """);
                    sb.append("기준 타점(<b>").append(entryText).append("</b>)을 밑돌아 상승 시나리오가 위협받고 있습니다.<br>");
                    sb.append("신규 진입을 멈추고, 보유 중이라면 <b>").append(stopText).append("</b> 부근에서 하락 브레이크가 걸리는지 확인 후 기계적으로 손절하세요.");
                    sb.append("</div>");
                }
            }
            case RECOVERY_WATCH -> {
                sb.append("""
        <div style='font-size:15px; font-weight:800; color:var(--text-dark); margin-bottom:6px;'>
            👀 정찰병 투입 또는 관망
        </div>
        <div style='font-size:14px; color:var(--text-secondary); line-height:1.5;'>
        """);
                if (currentPrice > entryPrice) {
                    sb.append("바닥을 다지는 초기 국면으로 아직 상승에 대한 확신을 갖기 이릅니다.<br>");
                    sb.append("타점(<b>").append(entryText).append("</b>) 부근까지 눌림이 올 때 극소액(정찰병)만 진입하거나, 확실한 거래량이 동반될 때까지 관망하세요.");
                } else {
                    sb.append("예상 지지선(<b>").append(entryText).append("</b>) 부근에서 하방 브레이크가 확실히 걸리는지 종가 마감까지 지켜봐야 합니다.<br>");
                    sb.append("바닥을 예측한 섣부른 매수보다는 <b>").append(fmt(ctx.ma5(), isUs)).append("</b>(5일선) 위로 안착하는 것을 확인한 후 진입하는 것이 안전합니다.");
                }
                sb.append("</div>");
            }
            case WATCH -> {
                sb.append("""
        <div style='font-size:15px; font-weight:800; color:var(--text-dark); margin-bottom:6px;'>
            ⏸️ 방향성 미확정 (매매 휴식)
        </div>
        <div style='font-size:14px; color:var(--text-secondary); line-height:1.5;'>
        """);
                sb.append("지금 포지션을 취하는 것은 방향을 찍어 맞추는 <b>'동전 던지기(홀짝 베팅)'</b>에 불과합니다.<br>");

                if (currentPrice > ctx.ma20()) {
                    sb.append("주가가 생명선(<b>").append(fmt(ctx.ma20(), isUs)).append("</b>)을 돌파했으나, 거래량과 추세 강도(ADX)가 부족해 신뢰도가 떨어집니다.<br>");
                    sb.append("언제든 다시 꺾이는 휩쏘(속임수) 가능성이 높으므로, 확실한 매수세가 붙을 때까지 관망하세요.");
                } else {
                    sb.append("기회비용을 낭비하지 마시고, 확실한 매수 거래량과 함께 주요 저항선인 <b>").append(fmt(ctx.ma20(), isUs)).append("(20일선)을 강하게 돌파</b>할 때까지 타점 진입을 보류하세요.");
                }
                sb.append("</div>");
            }
            default -> {
                sb.append("""
        <div style='font-size:15px; font-weight:800; color:var(--text-dark); margin-bottom:6px;'>
            ⏳ 분석 신호 대기 중
        </div>
        <div style='font-size:14px; color:var(--text-secondary); line-height:1.5;'>
            차트 데이터가 충분히 누적되지 않아 명확한 행동 지침을 산출할 수 없습니다.
        </div>
        """);
            }
        }

        boolean isEntryPhase = (phase == StockPhase.RECOVERY_WATCH
                || phase == StockPhase.SETUP
                || phase == StockPhase.ENTRY_READY
                || phase == StockPhase.STRONG_HOLD);
        if (isEntryPhase) {
            if (rr > 0 && rr < 0.6) {
                sb.append("""
<div style='margin-top:12px; font-size:14px; color:var(--toss-red); font-weight:600;'>
    🚨 기대 수익 대비 리스크가 큰 자리 (진입 비권장)
</div>
""");
            } else if (rr >= 0.6 && rr < 1.2) {
                sb.append("""
<div style='margin-top:12px; font-size:14px; color:var(--toss-orange); font-weight:600;'>
    💡 단기 저항이 가까운 구간 (확실한 눌림목 매수 권장)
</div>
""");
            } else if (rr >= 1.2) {
                sb.append("""
<div style='margin-top:12px; font-size:14px; color:var(--toss-blue); font-weight:600;'>
    ✅ 손익비 우수 (충분한 상승 여력 확보)
</div>
""");
            }
        }

        sb.append("</div>");

        return sb.toString();
    }

    private void appendPricingUnavailableMessage(StringBuilder sb) {
        sb.append("""
        <div style='font-size:15px; font-weight:800; color:var(--text-dark); margin-bottom:6px;'>
            가격 기준 확인 필요
        </div>
        <div style='font-size:14px; color:var(--text-secondary); line-height:1.5;'>
            유효한 진입가 또는 손절가가 산출되지 않아 구체적인 매매 구간을 제시하기 어려움
        </div>
        """);
    }

    private String getPhaseFactBriefing(StockPhase phase, MarketContext ctx) {
        return switch (phase) {
            case DANGER_ZONE -> {
                if (ctx.projectedVolume() > ctx.volumeMa5() * 2.0) {
                    yield "대량 매도세가 동반된 강한 하락 추세 및 역배열 상태";
                }
                if (ctx.rsi() < 30) {
                    yield "과매도권에 진입한 완연한 하락 추세";
                }
                yield "주요 이평선을 이탈한 지속적인 하락 국면";
            }
            case RECOVERY_WATCH -> {
                if (ctx.macd().isGoldenCross()) {
                    yield "MACD 골든크로스가 동반된 바닥 다지기 및 반등 시도";
                }
                if (ctx.currentPrice() > ctx.ma5()) {
                    yield "단기 이평선(5일선)을 회복하며 추세 전환을 모색하는 구간";
                }
                yield "급락 이후 변동성이 축소되며 바닥을 탐색하는 초기 회복 국면";
            }
            case WATCH -> {
                if (ctx.isSqueeze()) {
                    yield "볼린저 밴드 밀집으로 조만간 큰 변동성이 예상되는 관망 구간";
                }
                if (ctx.currentPrice() < ctx.ma20()) {
                    yield "주요 지지선 이탈 후 방향성을 탐색하는 관망 구간";
                }
                if (ctx.isSideways()) {
                    yield "뚜렷한 매수/매도 우위 없이 박스권 횡보 중인 구간";
                }
                yield "추가적인 방향성 확인이 필요한 방향 탐색 구간";
            }
            case SETUP -> {
                if (Math.abs(ctx.distFromMa20()) < (ctx.atr() * 0.5)) {
                    yield "20일선 핵심 지지선 부근에서 형성된 이상적인 단기 눌림목";
                }
                yield "중장기 상승 구조 속에서 에너지를 응축하는 건전한 조정 구간";
            }
            case ENTRY_READY -> {
                if (ctx.currentPrice() > ctx.yesterdayPrice() * 1.03) {
                    yield "강한 갭상승을 동반하며 핵심 저항선을 돌파한 진입 타이밍";
                }
                if (ctx.projectedVolume() > ctx.volumeMa5() * 1.5) {
                    yield "거래량 급증과 함께 유의미한 지지선 반등이 포착된 타점";
                }
                yield "지지선 방어에 성공하며 상방으로 방향을 튼 진입 구간";
            }
            case STRONG_HOLD -> {
                if (ctx.adx() > 40) {
                    yield "ADX 40 이상의 폭발적인 추세 강도가 돋보이는 강력한 상승 구간";
                }
                if (ctx.percentB() > 0.8) {
                    yield "볼린저 밴드 상단에서 시세를 분출 중인 정배열 상승 국면";
                }
                yield "매수세가 시장 장악하며 우상향 추세가 지속되는 홀딩 구간";
            }
            case WARNING_ZONE -> {
                if (ctx.rsi() > 80) {
                    yield "RSI 80 초과로 극단적 과매수 상태에 도달한 단기 과열 구간";
                }
                if (!ctx.macd().isHistogramRising()) {
                    yield "상승세는 유지 중이나 MACD 모멘텀 둔화가 포착된 주의 구간";
                }
                yield "단기 급등에 따른 피로도 누적으로 차익 실현 매물이 예상되는 구간";
            }
            case SCALE_OUT -> {
                if (ctx.isVolBearish()) {
                    yield "고점 부근에서 매도 거래량이 출회되며 상승 탄력이 둔화된 상태";
                }
                yield "주요 저항대 진입 및 과열 부담으로 인해 기계적인 분할 익절이 필요한 상태";
            }
            case COOLDOWN -> {
                if (ctx.projectedVolume() > ctx.volumeMa5() * 3.0) {
                    yield "패닉 셀링이 동반되며 핵심 지지선이 붕괴된 투매 발생 구간";
                }
                yield "단기 급락으로 인해 투자 심리가 얼어붙은 매매 휴식 구간";
            }
            default -> "시장 데이터 수집 및 차트 추세 분석 진행 중";
        };
    }
}