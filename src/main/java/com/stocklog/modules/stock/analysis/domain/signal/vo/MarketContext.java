package com.stocklog.modules.stock.analysis.domain.signal.vo;

import com.stocklog.modules.common.finance.vo.MacdResult;
import com.stocklog.modules.common.finance.vo.Pivot;
import com.stocklog.modules.stock.analysis.utils.IndicatorCalculator;
import com.stocklog.modules.stock.chart.dto.StockChartDto;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

// 분석 시점의 모든 시장 데이터를 담고 있는 불변 객체
@Builder
@Slf4j
public record MarketContext(
        // ── 1. 기본 차트 및 가격 데이터 ──
        List<StockChartDto> dailyCharts,
        List<StockChartDto> weeklyCharts,
        int lastIdx,
        List<Double> closes,
        List<Double> highs,
        List<Double> lows,
        List<Double> volumes,
        double currentPrice,
        double yesterdayPrice,

        // ── 2. 이동평균선 (MA) 및 이격도 ──
        double ma5,
        double ma20,
        double ma50,
        double ma5Yesterday,
        double ma20Yesterday,
        boolean isMa5Rising,
        boolean isMa20Rising,
        double distFromMa5,
        double distFromMa20,
        double distFromMa50,

        // ── 3. 볼린저 밴드 ──
        double stdDev20,
        double upper,
        double lower,
        double bandWidth,
        double percentB,
        double dynamicThreshold,
        boolean isSqueeze,

        // ── 4. 모멘텀 및 추세 지표 (RSI, ATR, ADX) ──
        double rsi,
        double atr,
        double adx,
        boolean isStrongTrend,
        boolean isWeakTrend,

        // ── 5. 거래량 분석 ──
        double currentVolume,
        double projectedVolume,
        double volumeMa5,
        boolean isVolBullish,
        boolean isVolBearish,
        boolean isVolumeDryingUp,
        double vwap20,       // 20일 기준 VWAP
        double pocPrice,     // 최근 60일 기준 최대 매물대 (POC)

        // ── 6. Price Action 및 거시 추세 ──
        boolean isHH,
        boolean isHL,
        boolean isLH,
        boolean isLL,
        boolean isMacroUptrend,
        boolean isUptrend,
        boolean isSideways,



        // ── 7. 복합 지표 객체 ──
        MacdResult macd,
        Pivot dailyPivot,
        Pivot weeklyPivot
) {

    public static MarketContext getMarketContext(List<StockChartDto> dailyCharts, List<StockChartDto> weeklyCharts) {
        if (dailyCharts == null || dailyCharts.size() < 60) {
            return null; // 데이터 부족 시 방어 로직
        }

        List<Double> closes = dailyCharts.stream().map(b -> b.close().doubleValue()).collect(Collectors.toList());
        List<Double> highs = dailyCharts.stream().map(b -> b.high().doubleValue()).collect(Collectors.toList());
        List<Double> lows = dailyCharts.stream().map(b -> b.low().doubleValue()).collect(Collectors.toList());
        List<Double> volumes = dailyCharts.stream().map(b -> b.volume() != null ? b.volume().doubleValue() : 0.0).collect(Collectors.toList());

        int lastIdx = closes.size() - 1;
        double cur = closes.get(lastIdx);
        double prev = closes.get(lastIdx - 1);

        // ── 이동평균선 연산 ──
        double ma5 = IndicatorCalculator.sma(closes, lastIdx, 5);
        double ma20 = IndicatorCalculator.sma(closes, lastIdx, 20);
        double ma50 = lastIdx >= 49 ? IndicatorCalculator.sma(closes, lastIdx, 50) : Double.NaN;
        double ma5Yesterday = IndicatorCalculator.sma(closes, lastIdx - 1, 5);
        double ma20Yesterday = IndicatorCalculator.sma(closes, lastIdx - 1, 20);

        boolean isMa5Rising = ma5 > ma5Yesterday;
        boolean isMa20Rising = ma20 > ma20Yesterday;

        double distFromMa5 = (cur - ma5) / ma5 * 100;
        double distFromMa20 = (cur - ma20) / ma20 * 100;
        double distFromMa50 = !Double.isNaN(ma50) ? (cur - ma50) / ma50 * 100 : Double.NaN;

        // ── 볼린저 밴드 연산 ──
        double stdDev20 = IndicatorCalculator.stdDev(closes, lastIdx, 20);
        double upper = ma20 + 2 * stdDev20;
        double lower = ma20 - 2 * stdDev20;
        double bandWidth = ma20 > 0 ? (upper - lower) / ma20 * 100 : 0;
        double dynamicThreshold = Math.max(5.0, bandWidth * 0.5);
        double percentB = (upper - lower) > 0 ? (cur - lower) / (upper - lower) : 0.5;
        boolean isSqueeze = IndicatorCalculator.isBollingerSqueezeOptimized(closes, lastIdx, 20);

        // ── 모멘텀 연산 ──
        double rsi = IndicatorCalculator.calcRsiWilder(closes, 14, lastIdx);
        double atr = IndicatorCalculator.calcAtr(highs, lows, closes, 14, lastIdx);
        double adx = IndicatorCalculator.calcAdx(highs, lows, closes, 14, lastIdx);
        boolean isStrongTrend = adx >= 25;
        boolean isWeakTrend = adx < 20;

        // ── 거래량 연산 ──
        double currentVolume = volumes.get(lastIdx);
        double yesterdayVolume = volumes.get(lastIdx - 1);
        LocalDate chartDate = dailyCharts.get(lastIdx).date();

        double projectedVolume = IndicatorCalculator.calculateProjectedVolume(currentVolume,yesterdayVolume, chartDate);
        double volumeMa5 = IndicatorCalculator.sma(volumes, lastIdx - 1, 5);
        double volumeMa20 = IndicatorCalculator.sma(volumes, lastIdx - 1, 20);
        log.info("curVol:{}, yesVol:{}, yesyesVol:{} // projected:{}", currentVolume, yesterdayVolume, volumes.get(lastIdx - 2), projectedVolume);
        log.info("volMa5:{}, volMa20:{}", volumeMa5, volumeMa20);

        double volumeRatio = volumeMa20 > 0 ? projectedVolume / volumeMa20 : 0;
        boolean isVolumeDryingUp = volumeRatio < 0.7;
        boolean isStrongVolume = volumeRatio >= 1.5;

// 시가 데이터가 없고 전일 종가(prev)만 있는 경우
// 최소 0.5% 이상의 유의미한 변동이 동반될 때만 방향성 거래량으로 인정
        double priceChangeRatio = prev > 0 ? (cur - prev) / prev : 0;
        boolean isMeaningfulUp = priceChangeRatio > 0.005;   // +0.5% 이상 상승
        boolean isMeaningfulDown = priceChangeRatio < -0.005; // -0.5% 이하 하락

        boolean isVolBullish = isStrongVolume && isMeaningfulUp;
        boolean isVolBearish = isStrongVolume && isMeaningfulDown;

        double vwap20 = IndicatorCalculator.calcVwap(highs, lows, closes, volumes, lastIdx, 20);
        double pocPrice = IndicatorCalculator.calcPoc(highs, lows, closes, volumes, lastIdx, Math.min(60, closes.size()), 15);

        // ── Price Action 연산 ──
        int lookbackPeriod = 5; // 최근 1주일(5거래일) 기준

        // 어제(lastIdx - 1)를 기준으로 과거 5일간의 최고/최저점 도출
        double recentHigh5 = IndicatorCalculator.highest(highs, lastIdx - 1, lookbackPeriod);
        double recentLow5 = IndicatorCalculator.lowest(lows, lastIdx - 1, lookbackPeriod);

        // 오늘(lastIdx)의 가격이 최근 5일 박스권을 돌파/이탈 했는지 판별
        boolean isHH = highs.get(lastIdx) > recentHigh5;
        boolean isLL = lows.get(lastIdx) < recentLow5;

        // 약식 추세 지지 여부: 저점이 어제보다 높고, 최근 5일 바닥을 깨지 않음 (Higher Low)
        boolean isHL = lows.get(lastIdx) > lows.get(lastIdx - 1) && lows.get(lastIdx - 1) > recentLow5;
        // 약식 추세 저항 여부: 고점이 어제보다 낮고, 최근 5일 천장을 뚫지 못함 (Lower High)
        boolean isLH = highs.get(lastIdx) < highs.get(lastIdx - 1) && highs.get(lastIdx - 1) < recentHigh5;

        boolean isMacroUptrend = !Double.isNaN(ma50) && cur > ma50;
        boolean isUptrend = (ma5 > ma20) && (ma20 > ma50) && isMa20Rising;
        boolean isSideways = !isUptrend && adx < 20;

        // ── 복합 객체 연산 ──
        MacdResult macd = IndicatorCalculator.calcMacd(closes);

        StockChartDto yesterday = dailyCharts.get(lastIdx - 1);
        double yh = yesterday.high() != null ? yesterday.high().doubleValue() : prev;
        double yl = yesterday.low() != null ? yesterday.low().doubleValue() : prev;
        double yc = yesterday.close().doubleValue();
        Pivot dailyPivot = IndicatorCalculator.calcPivot(yh, yl, yc);

        Pivot weeklyPivot = null;
        if (weeklyCharts != null && weeklyCharts.size() >= 2) {
            StockChartDto lastWeek = weeklyCharts.get(weeklyCharts.size() - 2);
            double wyh = lastWeek.high() != null ? lastWeek.high().doubleValue() : lastWeek.close().doubleValue();
            double wyl = lastWeek.low() != null ? lastWeek.low().doubleValue() : lastWeek.close().doubleValue();
            double wyc = lastWeek.close().doubleValue();
            weeklyPivot = IndicatorCalculator.calcPivot(wyh, wyl, wyc);
        }

        // 완성된 데이터 조립 및 반환
        return MarketContext.builder()
                .dailyCharts(dailyCharts).weeklyCharts(weeklyCharts)
                .lastIdx(lastIdx).closes(closes).highs(highs).lows(lows).volumes(volumes)
                .currentPrice(cur).yesterdayPrice(prev)
                .ma5(ma5).ma20(ma20).ma50(ma50)
                .ma5Yesterday(ma5Yesterday).isMa5Rising(isMa5Rising)
                .ma20Yesterday(ma20Yesterday).isMa20Rising(isMa20Rising)
                .distFromMa5(distFromMa5).distFromMa20(distFromMa20).distFromMa50(distFromMa50)
                .stdDev20(stdDev20).upper(upper).lower(lower).bandWidth(bandWidth).percentB(percentB).dynamicThreshold(dynamicThreshold).isSqueeze(isSqueeze)
                .rsi(rsi).atr(atr).adx(adx).isStrongTrend(isStrongTrend).isWeakTrend(isWeakTrend)
                .currentVolume(currentVolume).projectedVolume(projectedVolume).volumeMa5(volumeMa5).isVolBullish(isVolBullish).isVolBearish(isVolBearish).isVolumeDryingUp(isVolumeDryingUp)
                .vwap20(vwap20).pocPrice(pocPrice)
                .isHH(isHH).isHL(isHL).isLH(isLH).isLL(isLL).isMacroUptrend(isMacroUptrend).isUptrend(isUptrend).isSideways(isSideways)
                .macd(macd).dailyPivot(dailyPivot).weeklyPivot(weeklyPivot)
                .build();
    }

    public static String generateVerdict(MarketContext ctx, StockPhase phase) {
        StringBuilder verdict = new StringBuilder();

        // ── 1. 헤더 및 현재가 ──
        verdict.append("📊 [마켓 컨텍스트 분석 리포트]\n");
        verdict.append(String.format("현재가: %,.0f원 (전일 대비 %s)\n",
                ctx.currentPrice(),
                ctx.currentPrice() >= ctx.yesterdayPrice() ? "▲ 상승" : "▼ 하락"));
        verdict.append("--------------------------------------\n");

        // ── 2. 추세 판단 (Price Action & Moving Average) ──
        verdict.append("📈 [추세 분석]: ");
        if (ctx.isMacroUptrend() && ctx.isUptrend()) {
            verdict.append("강한 장단기 상승 추세 진행 중. ");
            if (ctx.isHH() && ctx.isHL()) verdict.append("고점과 저점을 지속적으로 높이고(HH, HL) 있습니다. ");
        } else if (!ctx.isMacroUptrend() && !ctx.isUptrend()) {
            verdict.append("장단기 하락 추세 진행 중. ");
            if (ctx.isLL() && ctx.isLH()) verdict.append("고점과 저점이 지속적으로 낮아지는(LH, LL) 약세장입니다. ");
        } else if (ctx.isSideways() || ctx.isSqueeze()) {
            verdict.append("방향성을 탐색하는 횡보 국면입니다. ");
        } else {
            verdict.append("단기 및 장기 추세가 혼조세를 보이고 있습니다. ");
        }

        if (ctx.isStrongTrend()) {
            verdict.append(String.format("(ADX: %.1f - 매우 강한 추세)", ctx.adx()));
        }
        verdict.append("\n");

        // ── 3. 모멘텀 & 과열/침체 (RSI, MACD, Bollinger Bands) ──
        verdict.append("🔥 [모멘텀 및 변동성]: ");
        if (ctx.rsi() >= 70) {
            verdict.append(String.format("RSI(%.1f) 과열권 진입. 단기 조정 리스크가 존재합니다. ", ctx.rsi()));
        } else if (ctx.rsi() <= 30) {
            verdict.append(String.format("RSI(%.1f) 과매도 침체권. 기술적 반등 가능성이 있습니다. ", ctx.rsi()));
        } else {
            verdict.append(String.format("RSI(%.1f) 중립 수준. ", ctx.rsi()));
        }

        if (ctx.percentB() > 1.0) {
            verdict.append("볼린저 밴드 상단 돌파(밴드 이탈 강세). ");
        } else if (ctx.isSqueeze()) {
            verdict.append(String.format("볼린저 밴드 스퀴즈 발생(BandWidth: %.2f). 조만간 큰 변동성이 예상됩니다. ", ctx.bandWidth()));
        }
        verdict.append("\n");

        // ── 4. 수급 및 거래량 ──
        verdict.append("💰 [수급 및 거래량]: ");
        if (ctx.isVolBullish()) {
            verdict.append("긍정적 수급. 의미 있는 매수 거래량이 유입되었습니다. ");
        } else if (ctx.isVolBearish()) {
            verdict.append("부정적 수급. 강한 매도 거래량이 출회되었습니다. ");
        } else if (ctx.projectedVolume < ctx.volumeMa5()) {
            verdict.append("거래량이 부진하며 시장의 관심이 상대적으로 적습니다. ");
        }

        // ── 5. 주요 지지 및 저항 (VWAP, POC, Pivot) ──
        verdict.append("\n🛡️ [핵심 지지/저항 레벨]:\n");
        verdict.append(String.format("  - 단기 매물대(POC): %,.0f원\n", ctx.pocPrice()));
        verdict.append(String.format("  - 20일 기준 세력 평단(VWAP): %,.0f원\n", ctx.vwap20()));

        // 가격이 주요 레벨 위에 있는지 판단
        if (ctx.currentPrice() > ctx.pocPrice() && ctx.currentPrice() > ctx.vwap20()) {
            verdict.append("  👉 주요 매물대와 평균 단가 위에서 주가가 형성되어 매물 소화가 원활합니다.\n");
        } else if (ctx.currentPrice() < ctx.pocPrice()) {
            verdict.append("  👉 매물대(POC) 아래에 위치하여 상승 시 강한 저항이 예상됩니다.\n");
        }

        // ── 6. 종합 결론 및 대응 전략 (파라미터로 받은 phase 기준 매핑) ──
        verdict.append("--------------------------------------\n");
        verdict.append("💡 [최종 판단 요약]:\n");

        switch (phase) {
            case DANGER_ZONE -> {
                verdict.append("🚨 [하락 지속] 완연한 하락 추세 및 하방 압력 심화\n");
                if (ctx.isVolBearish()) {
                    verdict.append("⚠️ 특이동향: 대량 거래를 동반한 강한 매도세가 출회되었습니다. 투매 징후에 주의하세요.\n");
                }
                verdict.append("· 미보유자: 떨어지는 칼날입니다. 바닥이 완전히 확인될 때까지 신규 진입 절대 금지\n");
                verdict.append("· 보유자: 기술적 반등 시 비중 축소를 권장하며, 추세 이탈 시 기계적 리스크 관리 요망");
            }
            case RECOVERY_WATCH -> {
                verdict.append("🌱 [반등 초기] 큰 낙폭 이후 진정되며 바닥 다지기 진행\n");
                if (ctx.macd().isGoldenCross()) {
                    verdict.append("✨ 특이동향: MACD 골든크로스가 발생하며 하락 모멘텀이 둔화되고 있습니다.\n");
                }
                verdict.append("· 미보유자: 하락세가 멈추는지 관찰하는 구간. 완벽한 추세 전환 확인 전까지는 관망 우위\n");
                verdict.append("· 보유자: 섣부른 물타기는 자제하고, 단기 이평선을 완벽히 회복하는 시점까지 홀딩하며 대기");
            }
            case WATCH -> {
                verdict.append("⚠️ [방향 탐색] 뚜렷한 추세가 부재한 횡보 및 에너지 응축 구간\n");
                if (ctx.isSqueeze()) {
                    verdict.append("🔍 특이동향: 볼린저 밴드 밀집(스퀴즈) 상태로 조만간 큰 변동성이 발생할 수 있습니다.\n");
                }
                verdict.append("· 미보유자: 의미 있는 거래량을 동반한 상방 돌파(추세 이탈)를 확인한 후 진입 고려\n");
                verdict.append("· 보유자: 박스권 장세이므로 뚜렷한 상승 모멘텀 발생 전까지 비중 유지 및 관망");
            }
            case COOLDOWN -> {
                verdict.append("🧊 [매매 휴식] 변동성 확대 및 지지선 이탈에 따른 패닉 셀링 징후\n");
                if (ctx.currentPrice() < ctx.lower()) {
                    verdict.append("📉 특이동향: 볼린저 밴드 하단을 강하게 이탈하는 투매가 포착되었습니다.\n");
                }
                verdict.append("· 미보유자: 시장 심리가 무너진 상태입니다. 매매를 쉬며 뇌동매매를 방지하세요.\n");
                verdict.append("· 보유자: 과대 낙폭에 따른 일시적 기술적 반등 시 탈출 기회로 활용");
            }
            case SETUP -> {
                verdict.append("🌀 [단기 조정] 상승 추세 속 변동성이 축소되며 에너지를 모으는 눌림목 구간\n");
                if (!ctx.isVolBearish()) {
                    verdict.append("📊 특이동향: 강한 매도세 출회 없이, 거래량이 마르며 건전한 조정이 진행 중입니다.\n");
                }
                verdict.append("· 미보유자: 상승 추세 훼손이 없는지 확인하며, 단기 지지선 부근에서 분할 매수 타점 탐색\n");
                verdict.append("· 보유자: 추세가 꺾이지 않았으므로 섣부른 매도보다는 홀딩하며 재상승 랠 대기");
            }
            case ENTRY_READY -> {
                verdict.append("🎯 [진입 타이밍] 상승 추세 지지 확인 및 유의미한 매수세 유입\n");
                if (ctx.isVolBullish()) {
                    verdict.append("🔥 특이동향: 거래량이 급증하며 신뢰도 높은 추세 전환 및 돌파 시그널이 발생했습니다.\n");
                }
                if (ctx.rsi() > 65) {
                    verdict.append("⚠️ 주의: 단기 과열권(RSI 65+)에 진입 중이므로, 추격 매수 시 비중 조절이 필요합니다.\n");
                }
                verdict.append("· 미보유자: 명확한 기준(손절라인)을 설정하고 원칙에 따라 분할 진입 실행\n");
                verdict.append("· 보유자: 보유 물량을 유지하며 본격적인 상승 추세 탑승 준비");
            }
            case STRONG_HOLD -> {
                if (ctx.distFromMa20() > 15.0) {
                    verdict.append("🔥 [강세 지속] 초강세장이나 단기 이격도가 매우 커 변동성 리스크가 존재합니다.\n");
                    verdict.append("· 보유자: 수익을 극대화하되, 추세 꺾임 발생 시 즉각 대응할 수 있도록 익절가 상향\n");
                    verdict.append("· 미보유자: 신규 진입 절대 금지 (눌림목 조정 발생까지 대기)");
                } else {
                    verdict.append("🔥 [강세 지속] 확고한 상승 랠리 및 정배열 추세 진행 중\n");
                    if (ctx.adx() >= 40) {
                        verdict.append("🚀 특이동향: ADX 지수가 높아 매우 강력한 메가 트렌드가 형성되었습니다.\n");
                    }
                    verdict.append("· 미보유자: 상승 흐름 훼손 전까지 단기 지지선을 기준으로 한 트렌드 추종 유효\n");
                    verdict.append("· 보유자: 단기 추세선을 이탈하기 전까지는 매도를 보류하고 수익 극대화");
                }
            }
            case WARNING_ZONE -> {
                verdict.append("⚠️ [과열 주의] 단기 급등에 따른 추세 피로도 누적 및 지표 과열\n");
                if (ctx.rsi() >= 75) {
                    verdict.append(String.format("🚨 특이동향: RSI(%.1f) 극단적 과매수 구간입니다. 상승 모멘텀 둔화 시 급락에 대비하세요.\n", ctx.rsi()));
                }
                verdict.append("· 미보유자: 신규 매수 절대 자제 (기대 수익보다 조정 리스크가 훨씬 큽니다)\n");
                verdict.append("· 보유자: 수익 보존을 위해 방어적 태세로 전환하고 타이트한 익절 기준 적용");
            }
            case SCALE_OUT -> {
                verdict.append("💰 [수익 실현] 추세 오버슈팅 및 주요 저항대 도달\n");
                if (ctx.currentPrice() >= ctx.upper()) {
                    verdict.append("📈 특이동향: 주가가 볼린저 밴드 상단을 강하게 이탈하며 단기 슈팅이 나왔습니다.\n");
                }
                verdict.append("· 미보유자: 추격 매수는 매우 위험한 국면입니다. 관망하세요.\n");
                verdict.append("· 보유자: 과욕을 버리고 분할 매도로 수익을 확정 지은 후, 잔여 물량으로 추세 관망");
            }
            default -> verdict.append("💬 분석 데이터 대기 중");
        }

        return verdict.toString();
    }

    public static String generateEntryVerdict(MarketContext ctx, StockPhase phase) {
        StringBuilder verdict = new StringBuilder();

        // ── 1. 진입 타점 헤더 (신호등 직관적 표기) ──
        verdict.append("🎯 [AI 진입 타점 분석 코멘트]\n");
        verdict.append(String.format("현재가: %,.0f원\n", ctx.currentPrice()));

        // ── 2. 매수 관점의 긍정/부정 요인 팩트 체크 ──
        verdict.append("--------------------------------------\n");
        verdict.append("🔍 [매수 매력도 체크]\n");

        int buyScore = 0;

        // 긍정 요인 (매수 근거)
        if (ctx.isUptrend() || ctx.isMacroUptrend()) {
            verdict.append("  [+] 장단기 상승 추세 (정배열 우위)\n");
            buyScore++;
        }
        if (ctx.macd() != null && ctx.macd().isGoldenCross()) {
            verdict.append("  [+] MACD 골든크로스 발생 (상승 모멘텀)\n");
            buyScore++;
        }
        if (ctx.isVolBullish()) {
            verdict.append("  [+] 의미 있는 매수 거래량(스마트 머니) 유입 포착\n");
            buyScore++;
        }
        if (ctx.currentPrice() > ctx.pocPrice() && ctx.currentPrice() > ctx.vwap20()) {
            verdict.append("  [+] 주요 매물대(POC) 및 세력선(VWAP) 안착 완료\n");
            buyScore++;
        }

        // 부정 요인 (진입 리스크)
        if (!ctx.isUptrend() && !ctx.isMacroUptrend()) {
            verdict.append("  [-] 완연한 역배열 하락 추세 (바닥 확인 안됨)\n");
            buyScore--;
        }
        if (ctx.rsi() >= 70 || ctx.percentB() > 1.0) {
            verdict.append(String.format("  [-] 단기 과열권 (RSI: %.1f) - 고점 추격 매수 리스크\n", ctx.rsi()));
            buyScore--;
        }
        if (ctx.isVolBearish()) {
            verdict.append("  [-] 강한 매도 거래량 출회 (단기 하방 압력 강함)\n");
            buyScore--;
        }

        if (buyScore == 0) verdict.append("  [=] 뚜렷한 매수/매도 우위가 없는 중립 상태\n");
        verdict.append("\n");

        // ── 3. 실전 매수/대응 전략 (Phase 기반) ──
        verdict.append("💡 [AI 진입 코칭 및 전략]:\n");

        switch (phase) {
            case ENTRY_READY -> {
                verdict.append("🟢 [적극 매수] 돌파 및 추세 전환이 확인된 최적의 진입 구간입니다.\n");
                verdict.append("· 신규 진입: 현재가 부근에서 자신 있게 1차 매수 진입을 권장합니다.\n");
                verdict.append("· 추가 매수(보유자): 불타기(비중 확대)가 유효한 구간이며, 추세를 즐기세요.\n");
            }
            case SETUP -> {
                verdict.append("🟢 [분할 매수] 건전한 눌림목 조정이 진행 중인 안전한 진입 타점입니다.\n");
                if (ctx.isSqueeze()) verdict.append("  👉 볼린저 스퀴즈 발생 상태로 조만간 시세 분출(상승) 가능성이 높습니다.\n");
                verdict.append("· 신규 진입: 하락이 멈추는 지지선 부근에서 2~3회 분할 매수로 접근하세요.\n");
                verdict.append("· 추가 매수(보유자): 평단가를 관리하며 비중을 늘리기 매우 좋은 타점입니다.\n");
            }
            case RECOVERY_WATCH -> {
                verdict.append("🟡 [입질 매수/관망] 낙폭 과대 후 기술적 반등이 시작되는 초기 구간입니다.\n");
                verdict.append("· 신규 진입: 추세가 완전히 돌아서진 않았으므로, 정찰병(소액) 수준의 진입만 유효합니다.\n");
                verdict.append("· 추가 매수(보유자): 섣부른 물타기보다는 20일선(스윙 추세선) 완벽 돌파를 확인 후 비중을 실으세요.\n");
            }
            case WATCH -> {
                verdict.append("🟡 [관망 우위] 방향성이 결정되지 않은 박스권 횡보 장세입니다.\n");
                verdict.append("· 신규 진입: 지금 사면 지루한 시간을 보낼 수 있습니다. 확실한 돌파가 나올 때까지 대기하세요.\n");
                verdict.append("· 추가 매수(보유자): 추가 자금을 투입할 메리트가 적습니다. 현재 비중만 유지하세요.\n");
            }
            case STRONG_HOLD -> {
                verdict.append("🟠 [추격 매수 주의] 상승 추세는 강력하나, 신규 진입하기엔 가격적 부담이 큽니다.\n");
                if (ctx.distFromMa20() > 10.0) {
                    verdict.append(String.format("  👉 20일선과의 이격도(%.1f%%)가 커서 단기 조정 확률이 높습니다.\n", ctx.distFromMa20()));
                }
                verdict.append("· 신규 진입: 달리는 말에 올라타기엔 리스크가 큽니다. 눌림목(조정)이 올 때까지 인내하세요.\n");
                verdict.append("· 추가 매수(보유자): 신규 자금 투입은 금지하며, 보유 물량에 대한 익절 타이밍만 고민하세요.\n");
            }
            case WARNING_ZONE, SCALE_OUT -> {
                verdict.append("🔴 [신규 진입 금지] 지표가 극도로 과열되었거나 저항에 부딪힌 매도 국면입니다.\n");
                verdict.append("· 신규 진입: 고점에 물릴 확률이 매우 높습니다. 관심 종목에서 잠시 지워두세요.\n");
                verdict.append("· 추가 매수(보유자): 매수는 절대 금지하며, 즉시 분할 익절(수익 실현)을 시작해야 합니다.\n");
            }
            case DANGER_ZONE, COOLDOWN -> {
                verdict.append("🔴 [매수 절대 금지] 매수세가 실종되고 하방 압력이 강한 하락장입니다.\n");
                if (ctx.currentPrice() < ctx.pocPrice()) {
                    verdict.append("  👉 주요 지지선이 붕괴되어 떨어지는 칼날 형태입니다.\n");
                }
                verdict.append("· 신규 진입: 싸다고 함부로 사면 안 됩니다. 바닥 지지가 최소 3일 이상 확인될 때까지 관망하세요.\n");
                verdict.append("· 추가 매수(보유자): 물타기는 계좌를 녹입니다. 비중 축소 또는 관망으로 대응하세요.\n");
            }
            default -> verdict.append("💬 데이터 분석 대기 중...\n");
        }

        // ── 4. 기준가 가이드라인 (스마트 타점) ──
        verdict.append("\n🛡️ [단기 매매 기준선]\n");
        if (phase == StockPhase.SETUP || phase == StockPhase.ENTRY_READY) {
            // 매수하기 좋은 구간일 경우: 지지선을 '매수 목표가'로 제시
            verdict.append(String.format("· 1차 매수 타점(VWAP 세력선): %,.0f원 부근\n", ctx.vwap20()));
            verdict.append(String.format("· 2차 매수 타점(최대 매물대): %,.0f원 부근\n", ctx.pocPrice()));
            verdict.append(String.format("· 마지노선(손절 기준): %,.0f원 이탈 시\n", Math.min(ctx.vwap20(), ctx.pocPrice()) * 0.97)); // 약 3% 아래
        } else {
            // 관망/매도/하락장일 경우: 지지/저항 확인 용도로만 제시
            verdict.append(String.format("· 주요 저항대(매물대): %,.0f원\n", ctx.pocPrice()));
            verdict.append(String.format("· 단기 지지선(20일선): %,.0f원\n", ctx.ma20()));
        }

        return verdict.toString();
    }


    public static String generateExitVerdict(MarketContext ctx, StockPhase phase) {
        StringBuilder verdict = new StringBuilder();

        // ── 1. 매도 타점 헤더 (신호등 직관적 표기) ──
        verdict.append("🎯 [AI 매도 타점 분석 코멘트]\n");
        verdict.append(String.format("현재가: %,.0f원\n", ctx.currentPrice()));

        // ── 2. 하방 리스크 및 매도 압력 팩트 체크 ──
        verdict.append("--------------------------------------\n");
        verdict.append("📉 [하방 리스크 및 과열 체크]\n");

        int riskScore = 0;

        // 부정 요인 (매도 근거 / 하락 징후)
        if (!ctx.isUptrend() && !ctx.isMacroUptrend()) {
            verdict.append("  [!] 장단기 추세 하락 (이동평균선 역배열 진행 중)\n");
            riskScore++;
        }
        if (ctx.rsi() >= 70 || ctx.percentB() >= 1.0) {
            verdict.append(String.format("  [!] 단기 과열권 진입 (RSI: %.1f) - 차익 실현 매물 출회 가능성\n", ctx.rsi()));
            riskScore++;
        }
        if (ctx.isVolBearish()) {
            verdict.append("  [!] 대량 매도 거래량 출회 (세력 이탈 및 단기 고점 징후)\n");
            riskScore++;
        }
        if (ctx.currentPrice() < ctx.ma20()) {
            verdict.append("  [!] 스윙 생명선(20일선) 이탈 - 하방 추세 가속화 위험\n");
            riskScore++;
        }

        if (riskScore == 0) {
            verdict.append("  [+] 현재 뚜렷한 매도 시그널 및 단기 하방 리스크가 없습니다.\n");
        }
        verdict.append("\n");

        // ── 3. 실전 매도/대응 전략 (Phase 기반) ──
        verdict.append("💡 [AI 매도 코칭 및 전략]:\n");

        switch (phase) {
            case SCALE_OUT -> {
                verdict.append("🔴 [적극 익절] 시세 분출 및 오버슈팅이 발생한 최적의 매도 구간입니다.\n");
                verdict.append("· 수익 실현: 더 큰 욕심을 버리고 현재가 부근에서 50%~전량 익절을 권장합니다.\n");
                verdict.append("· 잔여 물량: 전량 매도가 아쉽다면, 5일선을 이탈하는 시점에 모두 청산하세요.\n");
            }
            case WARNING_ZONE -> {
                verdict.append("🟠 [분할 매도] 상승 피로도가 극에 달하며 단기 고점 징후가 보입니다.\n");
                verdict.append("· 리스크 관리: 갑작스러운 투매(급락)가 나올 수 있으므로 비중의 30% 이상을 현금화하세요.\n");
                verdict.append("· 하락 대비: 방어선(트레이링 스톱)을 타이트하게 끌어올려 수익금을 지켜야 합니다.\n");
            }
            case DANGER_ZONE -> {
                verdict.append("🔴 [강력 매도/손절] 추세가 꺾이고 주요 지지선이 붕괴된 위험 구간입니다.\n");
                verdict.append("· 손절 대응: 본전 생각에 미련을 두면 손실이 걷잡을 수 없이 커집니다. 기계적인 손절 및 비중 대폭 축소가 필요합니다.\n");
            }
            case COOLDOWN -> {
                verdict.append("🟡 [기술적 반등 시 매도] 과대 낙폭으로 인한 패닉셀이 휩쓸고 간 구간입니다.\n");
                verdict.append("· 물량 정리: 지금 당장 시장가로 던지기보다는, 데드캣(일시적 반등)이 나올 때 매도하여 손실을 최소화하는 전략이 유리합니다.\n");
            }
            case STRONG_HOLD -> {
                verdict.append("🟢 [홀딩 유지] 매도할 이유를 찾기 힘든 강력한 상승 랠리입니다.\n");
                verdict.append("· 수익 극대화: 섣부른 익절은 오히려 손해입니다. 추세가 꺾이는 명확한 시그널이 나오기 전까지 끝까지 들고 가세요.\n");
                verdict.append("· 전략: 주가가 오름에 따라 나의 최후 방어선(익절가)도 20일선 부근으로 계속 올려 잡으세요.\n");
            }
            case ENTRY_READY, SETUP -> {
                verdict.append("🟢 [매도 보류] 오히려 신규 매수를 고려해야 할 만큼 차트 흐름이 긍정적입니다.\n");
                verdict.append("· 홀딩: 지금 파는 것은 바닥에서 남에게 물량을 넘겨주는 격입니다. 본격적인 상승 랠리를 기다리세요.\n");
            }
            case WATCH -> {
                verdict.append("🟡 [관망/비중 조절] 방향성이 뚜렷하지 않은 지루한 박스권(횡보) 장세입니다.\n");
                verdict.append("· 기회비용: 자금이 묶이는 것이 싫다면 일부 매도하여 다른 종목으로 교체 매매를 고려해볼 수 있습니다.\n");
                verdict.append("· 홀딩 시: 이전 저점(지지선)을 깨지 않는 이상 관망하며 돌파를 기다리세요.\n");
            }
            case RECOVERY_WATCH -> {
                verdict.append("🟡 [매도 보류] 긴 하락을 멈추고 이제 막 턴어라운드를 시도하고 있습니다.\n");
                verdict.append("· 전략: 최저점에서 매도하는 실수를 피하세요. 최소한 단기 저항선까지의 반등을 지켜본 후 매도를 결정해도 늦지 않습니다.\n");
            }
            default -> verdict.append("💬 데이터 분석 대기 중...\n");
        }

        // ── 4. 기준가 가이드라인 (스마트 매도 타점) ──
        verdict.append("\n🛡️ [매도 기준선 가이드]\n");

        if (phase == StockPhase.SCALE_OUT || phase == StockPhase.WARNING_ZONE) {
            // 매도(익절) 해야 할 구간: 위쪽 저항선과 짧은 방어선을 제시
            verdict.append(String.format("· 1차 익절 목표가(볼린저 밴드 상단): %,.0f원 부근\n", ctx.upper()));
            if (ctx.pocPrice() > ctx.currentPrice()) {
                verdict.append(String.format("· 2차 익절 저항대(최대 매물대): %,.0f원 부근\n", ctx.pocPrice()));
            }
            verdict.append(String.format("· 단기 추세 방어선(5일선 이탈 시 컷): %,.0f원\n", ctx.ma5()));
        } else {
            // 홀딩 및 하락 구간: 나의 자산을 지키는 하단 마지노선(손절가) 제시
            verdict.append(String.format("· 최후 방어선(스윙 생명선 20일선): %,.0f원\n", ctx.ma20()));
            if (ctx.currentPrice() > ctx.pocPrice()) {
                verdict.append(String.format("· 강력한 지지선(최대 매물대 POC): %,.0f원\n", ctx.pocPrice()));
            }
            verdict.append(String.format("· 하락 시 최악의 마지노선(볼린저 하단): %,.0f원\n", ctx.lower()));
        }

        return verdict.toString();
    }
}
