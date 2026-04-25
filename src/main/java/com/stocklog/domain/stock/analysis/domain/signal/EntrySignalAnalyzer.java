package com.stocklog.domain.stock.analysis.domain.signal;

import com.stocklog.domain.stock.dto.vo.price.StockChartData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class EntrySignalAnalyzer {

    // ── 신호 가중치 (중요도 반영) ──
    private static final int W_MACD_CROSS   = 30;  // MACD 크로스: 강한 추세 전환 신호
    private static final int W_RSI_EXTREME  = 25;  // RSI 극단: 신뢰도 높은 반전 신호
    private static final int W_CANDLE       = 20;  // 캔들 패턴: 직접적 수급 반영
    private static final int W_BOLLINGER    = 15;  // 볼린저 이탈: 통계적 이상 구간
    private static final int W_PIVOT        = 15;  // 피벗: 단기 지지/저항
    private static final int W_MA_CROSS     = 10;  // MA 이격/눌림: 보조 신호
    private static final int W_VOL_CONFIRM  = 10;  // 거래량 동반 보너스

    public EntrySignal analyze(List<StockChartData> dailyBars) {

        if (dailyBars == null || dailyBars.size() < 30) {
            return EntrySignal.builder()
                    .buySignals(List.of("데이터 부족 — 일봉 30개 이상 필요"))
                    .sellSignals(List.of())
                    .buyStrength(0).sellStrength(0)
                    .build();
        }

        // 1. 시간축 정상화: index 0 = 과거, index [size-1] = 오늘
        List<StockChartData> chronologicalBars = new ArrayList<>(dailyBars);
        if (chronologicalBars.get(0).getTime().compareTo(chronologicalBars.get(chronologicalBars.size() - 1).getTime()) > 0) {
            Collections.reverse(chronologicalBars);
        }

        List<Double> closes = chronologicalBars.stream()
                .map(b -> b.getValue().doubleValue())
                .collect(Collectors.toList());

        List<Double> volumes = chronologicalBars.stream()
                .map(b -> b.getVolume() != null ? b.getVolume().doubleValue() : 0.0)
                .collect(Collectors.toList());

        int lastIdx = closes.size() - 1;

        List<String> buySignals  = new ArrayList<>();
        List<String> sellSignals = new ArrayList<>();
        int buyWeight  = 0;
        int sellWeight = 0;

        // ── 기본 지표 계산 (오늘 기준) ──
        double cur   = closes.get(lastIdx);
        double prev  = closes.get(lastIdx - 1);
        double ma5   = sma(closes, lastIdx, 5);
        double ma20  = sma(closes, lastIdx, 20);
        double ma50  = lastIdx >= 49 ? sma(closes, lastIdx, 50) : Double.NaN;

        double stdDev20 = stdDev(closes, lastIdx, 20);
        double upper = ma20 + 2 * stdDev20;
        double lower = ma20 - 2 * stdDev20;

        // RSI 최적화 (전체 데이터 순방향 계산)
        double rsi   = calcRsiWilder(closes, 14, lastIdx);

        // 거래량 동반 추세 판별
        double curVolume = volumes.get(lastIdx);
        double volumeMa5 = sma(volumes, lastIdx, 5);
        boolean isVolBullish = (cur > prev) && (curVolume > volumeMa5 * 1.2);
        boolean isVolBearish = (cur < prev) && (curVolume > volumeMa5 * 1.2);

        // ── MACD (EMA 기반) ──
        MacdResult macdResult = calcMacd(closes);
        double macdValue = macdResult.macd;
        double macdSignal = macdResult.signal;
        double macdHist   = macdResult.histogram;

        // ── 피벗 포인트 (전일 OHLC) ──
        StockChartData yesterday = chronologicalBars.get(lastIdx - 1);
        double yh = yesterday.getHigh()  != null ? yesterday.getHigh().doubleValue()  : prev;
        double yl = yesterday.getLow()   != null ? yesterday.getLow().doubleValue()   : prev;
        double yc = yesterday.getValue().doubleValue();

        double pivot = (yh + yl + yc) / 3.0;
        double r1 = (2 * pivot) - yl;
        double s1 = (2 * pivot) - yh;
        double r2 = pivot + (yh - yl);
        double s2 = pivot - (yh - yl);

        // ── 보조 계산 ──
        double ma5Yesterday     = sma(closes, lastIdx - 1, 5);
        boolean isMa5Rising     = ma5 > ma5Yesterday;
        double distFromMa5      = (cur - ma5)  / ma5  * 100;
        double distFromMa20     = (cur - ma20) / ma20 * 100;
        double distFromMa50     = !Double.isNaN(ma50) ? (cur - ma50) / ma50 * 100 : Double.NaN;

        double bandWidth        = ma20 > 0 ? (upper - lower) / ma20 * 100 : 0;
        double dynamicThreshold = Math.max(5.0, bandWidth * 0.5);
        double percentB         = (upper - lower) > 0 ? (cur - lower) / (upper - lower) : 0.5;
        boolean isSqueeze = isSqueeze(closes, lastIdx, 60); // 최근 60일 대비 응축도 확인

        // ── [1] 단기 추세 분석 (MA5) ──
        if (cur > ma5) {
            if (isMa5Rising) {
                if (distFromMa5 > dynamicThreshold) {
                    sellSignals.add(String.format("[이격과열] 5일선 대비 +%.1f%% — 조정 주의", distFromMa5));
                    sellWeight += W_MA_CROSS;
                } else {
                    buySignals.add(String.format("[5일선 상방] 안착 및 탄력 유지 (기준: %.0f)", ma5));
                    buyWeight += W_MA_CROSS;
                }
            } else {
                buySignals.add(String.format("5일선 돌파(기울기 하락) — 기술적 반등 진행 중, 안착 확인 필요 (MA5: %.0f)", ma5));
                buyWeight += W_MA_CROSS / 2;
            }
        } else {
            if (!isMa5Rising) {
                if (Math.abs(distFromMa5) > dynamicThreshold) {
                    buySignals.add(String.format("[과대낙폭] 5일선 이격 %.1f%% — 반등 기대", distFromMa5));
                    buyWeight += W_MA_CROSS;
                } else {
                    sellSignals.add(String.format("[5일선 하방] 단기 추세 꺾임 (기준: %.0f)", ma5));
                    sellWeight += W_MA_CROSS;
                }
            } else {
                if (Math.abs(distFromMa5) > 3.0) {
                    sellSignals.add(String.format("상승 5일선 하향 이탈 — 지지선 훼손 주의 (MA5: %.0f)", ma5));
                    sellWeight += W_MA_CROSS;
                } else {
                    buySignals.add(String.format("5일선 일시 하회 — 눌림목 지지 테스트 (MA5: %.0f)", ma5));
                    buyWeight += W_MA_CROSS / 2;
                }
            }
        }

        // ── [2] 피벗 지지/저항 ──
        if (cur >= s1 * 0.99 && cur <= s1 * 1.01) {
            buySignals.add(String.format("피벗 1차 지지선 근접 (S1: %.0f)", s1));
            buyWeight += W_PIVOT;
        }
        if (cur <= s2 * 1.01) {
            buySignals.add(String.format("피벗 2차 지지선 도달 — 강한 기술적 반등 기대 (S2: %.0f)", s2));
            buyWeight += W_PIVOT;
        }
        if (cur >= r1 * 0.99 && cur <= r1 * 1.01) {
            sellSignals.add(String.format("피벗 1차 저항선 근접 (R1: %.0f)", r1));
            sellWeight += W_PIVOT;
        }
        if (cur >= r2 * 0.99) {
            if (isVolBullish) {
                buySignals.add(String.format("피벗 2차 저항선 돌파 (거래량 동반) — 강한 추가 상승 기대 (R2: %.0f)", r2));
                buyWeight += W_PIVOT + W_VOL_CONFIRM;
            } else {
                sellSignals.add(String.format("피벗 2차 저항선 도달 (거래량 부족) — 단기 과열 주의 (R2: %.0f)", r2));
                sellWeight += W_PIVOT;
            }
        }

        // ── [3] RSI ──
        if (rsi < 25) {
            buySignals.add(String.format("RSI %.1f — 극단적 과매도 (강한 반등 신호)", rsi));
            buyWeight += W_RSI_EXTREME;
        } else if (rsi < 35) {
            buySignals.add(String.format("RSI %.1f — 과매도 구간", rsi));
            buyWeight += W_RSI_EXTREME / 2;
        } else if (rsi > 75) {
            if (isMa5Rising && distFromMa5 < dynamicThreshold) {
                sellSignals.add(String.format("RSI %.1f (과매수) — 단, 추세 이탈 없음 (성급한 익절 보류)", rsi));
                sellWeight += W_RSI_EXTREME / 2;
            } else {
                sellSignals.add(String.format("RSI %.1f — 극단적 과매수 및 이격 과열 (조정 주의)", rsi));
                sellWeight += W_RSI_EXTREME;
            }
        } else if (rsi > 65) {
            sellSignals.add(String.format("RSI %.1f — 과매수 구간", rsi));
            sellWeight += W_RSI_EXTREME / 2;
        }

        // ── [4] 볼린저 밴드 ──
        if (cur < lower) {
            buySignals.add(String.format("볼린저 하단 이탈 (현재 %.0f / 하단 %.0f) %%B: %.2f", cur, lower, percentB));
            buyWeight += W_BOLLINGER;
        } else if (cur > upper) {
            if (isVolBullish) {
                buySignals.add(String.format("볼린저 상단 돌파 (거래량 동반) — 강한 추세 시작 기대 (상단 %.0f)", upper));
                buyWeight += W_BOLLINGER + W_VOL_CONFIRM;
            } else {
                sellSignals.add(String.format("볼린저 상단 이탈 (단기 과열) (현재 %.0f / 상단 %.0f)", cur, upper));
                sellWeight += W_BOLLINGER;
            }
        }

        // ── [4-1] 볼린저 스퀴즈 ──
        if (isBollingerSqueezeOptimized(closes, lastIdx, 20)) {
            buySignals.add(String.format("볼린저 스퀴즈 감지 (밴드폭 %.1f%%) — 변동성 확장 임박", bandWidth));
            buyWeight += W_BOLLINGER / 2;
        }

        // ── [5] MACD ──
        if (macdResult.isGoldenCross) {
            buySignals.add(String.format("MACD 골든크로스 (MACD: %.2f / Signal: %.2f)", macdValue, macdSignal));
            buyWeight += W_MACD_CROSS;
        } else if (macdHist > 0 && macdResult.isHistogramRising) {
            buySignals.add(String.format("MACD 히스토그램 확장 중 (%.2f → 양전환 탄력)", macdHist));
            buyWeight += W_MACD_CROSS / 2;
        }

        if (macdResult.isDeadCross) {
            sellSignals.add(String.format("MACD 데드크로스 (MACD: %.2f / Signal: %.2f)", macdValue, macdSignal));
            sellWeight += W_MACD_CROSS;
        } else if (macdHist < 0 && !macdResult.isHistogramRising) {
            sellSignals.add(String.format("MACD 히스토그램 감소 중 (%.2f → 하락 압력)", macdHist));
            sellWeight += W_MACD_CROSS / 2;
        }

        // ── [6] MA20 / MA50 중장기 분석 ──
        if (distFromMa20 >= -1 && distFromMa20 <= 2) {
            buySignals.add(String.format("MA20 지지선 근접 (이격도 %.1f%%)", distFromMa20));
            buyWeight += W_MA_CROSS;
        } else if (distFromMa20 >= -3 && distFromMa20 < -1) {
            sellSignals.add(String.format("MA20 하향 이탈 (이격도 %.1f%%)", distFromMa20));
            sellWeight += W_MA_CROSS;
        }

        if (!Double.isNaN(distFromMa50)) {
            if (distFromMa50 >= -1 && distFromMa50 <= 3) {
                buySignals.add(String.format("50일선 지지 테스트 (이격도 %.1f%%)", distFromMa50));
                buyWeight += W_MA_CROSS;
            } else if (distFromMa50 >= -4 && distFromMa50 < -1) {
                sellSignals.add(String.format("50일선 하향 이탈 (이격도 %.1f%%)", distFromMa50));
                sellWeight += W_MA_CROSS;
            }
        }

        // ── [7] 캔들 패턴 ──
        if (hasOhlc(chronologicalBars)) {
            CandleWeight cw = detectCandlePatterns(chronologicalBars, lastIdx, buySignals, sellSignals, isVolBullish, isVolBearish);
            buyWeight  += cw.buy;
            sellWeight += cw.sell;
        }

        // ✨ [8] 3단계 분할 매수/매도 타점 생성 로직 (Gap & 비율 보정)
        List<Double> validSupports = Arrays.asList(ma20, ma50, s1, s2, lower).stream()
                .filter(p -> !Double.isNaN(p) && p < cur * 0.995)
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());

        double entry1, entry2, entry3;

        if (!validSupports.isEmpty()) {
            entry1 = validSupports.get(0);
            if (cur - entry1 > cur * 0.04) entry1 = cur * 0.985; // 1차가 너무 멀면 -1.5%

            final double e1 = entry1;
            entry2 = validSupports.stream()
                    .filter(p -> p <= e1 - (cur * 0.02)) // 1차 대비 최소 2% 이격
                    .findFirst()
                    .orElse(entry1 * 0.975); // 없으면 1차의 -2.5%

            final double e2 = entry2;
            entry3 = validSupports.stream()
                    .filter(p -> p <= e2 - (cur * 0.03)) // 2차 대비 최소 3% 이격
                    .findFirst()
                    .orElse(entry2 * 0.965); // 없으면 2차의 -3.5%
        } else {
            entry1 = cur * 0.985; // -1.5%
            entry2 = cur * 0.960; // -4.0%
            entry3 = cur * 0.920; // -8.0%
        }

        List<Double> validResistances = Arrays.asList(ma20, ma50, upper, r1, r2).stream()
                .filter(p -> !Double.isNaN(p) && p > cur * 1.005)
                .sorted()
                .collect(Collectors.toList());

        double target1, target2, target3;

        if (!validResistances.isEmpty()) {
            target1 = validResistances.get(0);
            if (target1 - cur > cur * 0.06) target1 = cur * 1.025; // 1차가 너무 멀면 +2.5%

            final double t1 = target1;
            target2 = validResistances.stream()
                    .filter(p -> p >= t1 + (cur * 0.03)) // 1차 대비 최소 3% 이격
                    .findFirst()
                    .orElse(target1 * 1.04); // 없으면 1차의 +4%

            final double t2 = target2;
            target3 = validResistances.stream()
                    .filter(p -> p >= t2 + (cur * 0.05)) // 2차 대비 최소 5% 이격
                    .findFirst()
                    .orElse(target2 * 1.05); // 없으면 2차의 +5%
        } else {
            target1 = cur * 1.025; // +2.5%
            target2 = cur * 1.060; // +6.0%
            target3 = cur * 1.120; // +12.0%
        }

        int buyStrength  = Math.min(buyWeight,  100);
        int sellStrength = Math.min(sellWeight, 100);


        AnalysisContext ctx = new AnalysisContext(cur, ma5, ma20, rsi, upper, lower, macdResult, isMa5Rising, buyStrength, sellStrength, isSqueeze, bandWidth);
        String verdict = determineVerdict(ctx);


        return EntrySignal.builder()
                .buySignals(buySignals)
                .sellSignals(sellSignals)
                .buyStrength(buyStrength)
                .sellStrength(sellStrength)
                .rsi(rsi)
                .macd(macdValue)
                .bollingerUpper(upper)
                .bollingerLower(lower)
                .currentPrice(cur)
                .pivot(pivot)
                .suggestedEntry1(entry1).suggestedEntry2(entry2).suggestedEntry3(entry3)
                .suggestedTarget1(target1).suggestedTarget2(target2).suggestedTarget3(target3)
                .r1(r1).r2(r2).s1(s1).s2(s2)
                .verdict(verdict)
                .build();
    }

    // ── 내부 레코드 (파라미터 전달용) ──
    private record AnalysisContext(double cur, double ma5, double ma20, double rsi, double upper, double lower, MacdResult macd, boolean isMa5Rising, int buyStrength, int sellStrength, boolean isSqueeze, double bandWidth) {}

    private String determineVerdict(AnalysisContext ctx) {
        // 1. 변동성 폭발 (Squeeze & Breakout)
        if (ctx.isSqueeze) {
            if (ctx.cur > ctx.upper && ctx.buyStrength > 60)
                return "🚀 [상방 돌파] 에너지 응축 후 분출 시작. 강력한 추세 추종 구간입니다.";
            if (ctx.cur < ctx.lower && ctx.sellStrength > 60)
                return "📉 [하방 이탈] 변동성 응축 후 낙폭 확대. 추가 폭락 위험이 매우 높습니다.";
            return "🌀 [에너지 응축] 폭풍전야. 조만간 상하방 큰 방향성이 결정될 임박 구간입니다.";
        }

        // 2. 리스크 관리 (Extreme Overheat)
        double gapFromMa5 = (ctx.cur - ctx.ma5) / ctx.ma5 * 100;
        if (ctx.rsi > 85 || (ctx.cur > ctx.upper && gapFromMa5 > 5.0))
            return "⚠️ [추격 금지] 엔진 과열. 단기 조정 확률이 매우 높으니 관망하세요.";

        // 3. 역추세 및 바닥 확인 (Mean Reversion)
        if (ctx.rsi < 25 && ctx.cur < ctx.lower && ctx.buyStrength > 40)
            return "💎 [바닥 포착] 과매도 끝자락에서 반등 기미가 보입니다. 분할 매수 진입 적기입니다.";

        // 4. 상승 추세 및 눌림목 (Trend Following)
        if (ctx.cur > ctx.ma20 && ctx.isMa5Rising) {
            if (ctx.cur < ctx.ma5 && ctx.cur > ctx.ma20 * 1.02)
                return "🎯 [눌림목 매수] 우상향 중인 견조한 조정입니다. 5일선 부근 지지를 확인하세요.";
            if (ctx.macd.isGoldenCross)
                return "🔥 [추세 강화] 수급에 탄력이 붙었습니다. 비중을 확대해도 좋은 구간입니다.";
        }

        // 5. 하락 추세 및 탈출 (Bear Market)
        if (ctx.cur < ctx.ma20 && !ctx.isMa5Rising) {
            if (ctx.macd.isDeadCross)
                return "🚨 [탈출 신호] 추세 붕괴와 지표 악화 중첩. 리스크 관리가 최우선입니다.";
            if (ctx.cur > ctx.ma5 && ctx.rsi < 45)
                return "📉 [기술적 반등] 하락 추세 내 일시적 반등(Dead Cat). 속지 말고 비중을 줄이세요.";
            return "🐻 [약세 지속] 매수세가 실종되었습니다. 현금을 지키며 확실한 바닥을 기다리세요.";
        }

        // 6. 힘겨루기 및 수급 기반
        if (Math.abs(ctx.buyStrength - ctx.sellStrength) <= 15)
            return "👀 [방향성 탐색] 매수/매도 공방 팽팽. 주요 지지선 돌파 전까지 대기하세요.";
        if (ctx.buyStrength > ctx.sellStrength + 25) return "🟢 [수급 우위] 사는 힘이 압도적입니다. 긍정적 관점이 유지됩니다.";
        if (ctx.sellStrength > ctx.buyStrength + 25) return "🔴 [매도 압박] 차익 실현 및 투매 물량이 우세합니다. 보수적 접근이 필요합니다.";

        return "💬 [신호 대기] 현재 100일 분석 범위 내 특이 신호가 없는 구간입니다.";
    }

    private static class CandleWeight { int buy = 0, sell = 0; }

    private CandleWeight detectCandlePatterns(List<StockChartData> bars, int lastIdx, List<String> buy, List<String> sell, boolean isVolBullish, boolean isVolBearish) {
        CandleWeight cw = new CandleWeight();
        if (lastIdx < 2) return cw;

        StockChartData today = bars.get(lastIdx);
        StockChartData prev = bars.get(lastIdx - 1);

        if (today.getHigh() == null || today.getLow() == null || today.getOpen() == null) return cw;

        double o = today.getOpen().doubleValue();
        double h = today.getHigh().doubleValue();
        double l = today.getLow().doubleValue();
        double c = today.getValue().doubleValue();
        double body      = Math.abs(c - o);
        double fullRange = h - l;
        if (fullRange == 0) return cw;

        double lowerWick = Math.min(o, c) - l;
        double upperWick = h - Math.max(o, c);

        // 망치형
        if (lowerWick >= body * 2 && upperWick <= body * 0.5 && body > 0) {
            if (isVolBullish) {
                buy.add("망치형 캔들 + 상승 거래량 터짐 (강력한 반등 신호)");
                cw.buy += (W_CANDLE + W_VOL_CONFIRM);
            } else {
                buy.add("망치형 캔들 — 하락 후 반등 신호");
                cw.buy += W_CANDLE;
            }
        }
        // 역망치형
        if (upperWick >= body * 2 && lowerWick <= body * 0.5 && body > 0 && c > o) {
            if (isVolBullish) {
                buy.add("역망치형 캔들 + 상승 거래량 터짐 (매수 세력 강한 유입)");
                cw.buy += (W_CANDLE + W_VOL_CONFIRM) / 2;
            } else {
                buy.add("역망치형 캔들 — 매수 세력 유입 신호");
                cw.buy += W_CANDLE / 2;
            }
        }
        // 유성형
        if (upperWick >= body * 2 && lowerWick <= body * 0.5 && body > 0 && c < o) {
            if (isVolBearish) {
                sell.add("유성형 캔들 + 하락 거래량 터짐 (강력한 하락 반전 신호)");
                cw.sell += (W_CANDLE + W_VOL_CONFIRM);
            } else {
                sell.add("유성형 캔들 — 상승 후 반전 신호");
                cw.sell += W_CANDLE;
            }
        }
        // 도지
        if (body / fullRange < 0.05) {
            if (isVolBullish) {
                buy.add("도지 캔들 + 상승 거래량 (상승 전환 가능성)");
                cw.buy += (W_CANDLE + W_VOL_CONFIRM) / 3;
            } else if (isVolBearish) {
                sell.add("도지 캔들 + 하락 거래량 (하락 전환 가능성)");
                cw.sell += (W_CANDLE + W_VOL_CONFIRM) / 3;
            } else {
                buy.add("도지 캔들 — 추세 전환 가능성 (방향 확인 필요)");
                cw.buy += W_CANDLE / 3;
            }
        }

        // 장악형
        if (prev.getOpen() != null && prev.getValue() != null) {
            double po = prev.getOpen().doubleValue();
            double pc = prev.getValue().doubleValue();

            if (pc < po && c > o && c > po && o < pc) {
                if (isVolBullish) {
                    buy.add("상승장악형 + 거래량 수반 (매우 강한 매수 신호)");
                    cw.buy += (W_CANDLE + W_VOL_CONFIRM);
                } else {
                    buy.add("상승장악형 캔들 — 강한 매수 신호");
                    cw.buy += W_CANDLE;
                }
            }
            if (pc > po && c < o && c < po && o > pc) {
                if (isVolBearish) {
                    sell.add("하락장악형 + 매도 거래량 터짐 (매우 강한 매도 신호)");
                    cw.sell += (W_CANDLE + W_VOL_CONFIRM);
                } else {
                    sell.add("하락장악형 캔들 — 강한 매도 신호");
                    cw.sell += W_CANDLE;
                }
            }
        }

        // 적삼병 / 흑삼병
        if (lastIdx >= 2) {
            boolean three = true;
            boolean threeDown = true;
            for (int i = 0; i < 3; i++) {
                StockChartData b = bars.get(lastIdx - i);
                if (b.getOpen() == null) { three = false; threeDown = false; break; }
                if (b.getOpen().doubleValue() >= b.getValue().doubleValue()) three = false;
                if (b.getOpen().doubleValue() <= b.getValue().doubleValue()) threeDown = false;
            }

            if (three) {
                if (isVolBullish) {
                    buy.add("적삼병(3연속 양봉) + 상승 거래량 (추세 확정)");
                    cw.buy += (W_CANDLE + W_VOL_CONFIRM) / 2;
                } else {
                    buy.add("3연속 양봉 — 단기 매수 연속성 확인");
                    cw.buy += W_CANDLE / 2;
                }
            }

            if (threeDown) {
                if (isVolBearish) {
                    sell.add("흑삼병(3연속 음봉) + 하락 거래량 (하락 추세 확정)");
                    cw.sell += (W_CANDLE + W_VOL_CONFIRM) / 2;
                } else {
                    sell.add("3연속 음봉 — 단기 매도 연속성 확인");
                    cw.sell += W_CANDLE / 2;
                }
            }
        }

        return cw;
    }

    private boolean isBollingerSqueezeOptimized(List<Double> prices, int lastIdx, int lookback) {
        if (lastIdx < 40) return false;

        double currentBandWidth = (bollingerUpper(prices, lastIdx, 20) - bollingerLower(prices, lastIdx, 20)) / sma(prices, lastIdx, 20);
        double minBandWidth = Double.MAX_VALUE;

        for (int i = 1; i <= lookback; i++) {
            double bw = (bollingerUpper(prices, lastIdx - i, 20) - bollingerLower(prices, lastIdx - i, 20)) / sma(prices, lastIdx - i, 20);
            if (bw < minBandWidth) minBandWidth = bw;
        }

        return currentBandWidth <= minBandWidth * 1.10;
    }

    private static class MacdResult {
        double macd, signal, histogram;
        boolean isGoldenCross, isDeadCross, isHistogramRising;
    }

    private MacdResult calcMacd(List<Double> chronologicalPrices) {
        MacdResult r = new MacdResult();
        int size = chronologicalPrices.size();
        if (size < 35) return r;

        double[] ema12 = calcEmaArray(chronologicalPrices, 12);
        double[] ema26 = calcEmaArray(chronologicalPrices, 26);

        double[] macdLine = new double[size];
        for (int i = 0; i < size; i++) macdLine[i] = ema12[i] - ema26[i];

        double[] signalLine = calcEmaFromArray(macdLine, 9, 26);

        r.macd      = macdLine[size - 1];
        r.signal    = signalLine[size - 1];
        r.histogram = r.macd - r.signal;

        double prevMacd   = macdLine[size - 2];
        double prevSignal = signalLine[size - 2];
        r.isGoldenCross   = prevMacd <= prevSignal && r.macd > r.signal;
        r.isDeadCross     = prevMacd >= prevSignal && r.macd < r.signal;

        double prevHist = macdLine[size - 2] - signalLine[size - 2];
        r.isHistogramRising = r.histogram > prevHist;

        return r;
    }

    private double[] calcEmaArray(List<Double> prices, int period) {
        double[] ema = new double[prices.size()];
        if(prices.size() < period) return ema;

        double k = 2.0 / (period + 1);
        double sum = 0;
        for (int i = 0; i < period; i++) sum += prices.get(i);
        ema[period - 1] = sum / period;

        for (int i = period; i < prices.size(); i++) {
            ema[i] = prices.get(i) * k + ema[i - 1] * (1 - k);
        }
        return ema;
    }

    private double[] calcEmaFromArray(double[] src, int period, int startIndex) {
        double[] ema = new double[src.length];
        if(src.length < startIndex + period) return ema;

        double k = 2.0 / (period + 1);
        double sum = 0;
        for (int i = startIndex; i < startIndex + period; i++) sum += src[i];

        ema[startIndex + period - 1] = sum / period;
        for (int i = startIndex + period; i < src.length; i++) {
            ema[i] = src[i] * k + ema[i - 1] * (1 - k);
        }
        return ema;
    }

    private double calcRsiWilder(List<Double> prices, int period, int lastIdx) {
        if (lastIdx <= period) return 50;

        double avgGain = 0, avgLoss = 0;
        for (int i = 1; i <= period; i++) {
            double diff = prices.get(i) - prices.get(i - 1);
            if (diff > 0) avgGain += diff;
            else          avgLoss -= diff;
        }
        avgGain /= period;
        avgLoss /= period;

        for (int i = period + 1; i <= lastIdx; i++) {
            double diff = prices.get(i) - prices.get(i - 1);
            double gain = diff > 0 ? diff : 0;
            double loss = diff < 0 ? -diff : 0;

            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
        }

        if (avgLoss == 0) return 100;
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    private double sma(List<Double> prices, int endIdx, int period) {
        if (endIdx < period - 1) return 0;
        double sum = 0;
        for (int i = endIdx; i > endIdx - period; i--) sum += prices.get(i);
        return sum / period;
    }

    private double stdDev(List<Double> prices, int endIdx, int period) {
        if (endIdx < period - 1) return 0;
        double mean = sma(prices, endIdx, period);
        double sumSq = 0;
        for (int i = endIdx; i > endIdx - period; i--) sumSq += Math.pow(prices.get(i) - mean, 2);
        return Math.sqrt(sumSq / period);
    }

    private double bollingerUpper(List<Double> prices, int endIdx, int period) {
        return sma(prices, endIdx, period) + 2 * stdDev(prices, endIdx, period);
    }

    private double bollingerLower(List<Double> prices, int endIdx, int period) {
        return sma(prices, endIdx, period) - 2 * stdDev(prices, endIdx, period);
    }

    private boolean hasOhlc(List<StockChartData> bars) {
        return !bars.isEmpty() && bars.get(bars.size()-1).getHigh() != null;
    }

    private boolean isSqueeze(List<Double> prices, int lastIdx, int lookback) {
        if (lastIdx < lookback) return false;

        // 1. 현재 밴드폭 계산
        double currentUpper = bollingerUpper(prices, lastIdx, 20);
        double currentLower = bollingerLower(prices, lastIdx, 20);
        double currentMa    = sma(prices, lastIdx, 20);

        if (currentMa == 0) return false;
        double currentWidth = (currentUpper - currentLower) / currentMa;

        // 2. 과거 lookback 기간 동안의 최저 밴드폭 탐색
        double minWidth = Double.MAX_VALUE;
        for (int i = 1; i <= lookback; i++) {
            int targetIdx = lastIdx - i;
            double upper = bollingerUpper(prices, targetIdx, 20);
            double lower = bollingerLower(prices, targetIdx, 20);
            double ma    = sma(prices, targetIdx, 20);

            if (ma > 0) {
                double width = (upper - lower) / ma;
                if (width < minWidth) minWidth = width;
            }
        }

        // 3. 현재 밴드폭이 과거 최저치에 근접(예: 1.1배 이내)하면 Squeeze로 판단
        // 또는 최근 N일 중 하위 10% 이내일 때 등으로 정의 가능
        return currentWidth <= minWidth * 1.10;
    }
}