package com.stocklog.modules.stock.analysis;

import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.common.finance.model.ExchangeSession;
import com.stocklog.modules.common.type.ChartDataType;
import com.stocklog.modules.stock.analysis.signal.trading.TradingSignalResult;
import com.stocklog.modules.stock.chart.StockChartService;
import com.stocklog.modules.stock.chart.dto.StockChartDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** 보유 종목의 '보유 기준 손절선'을 종목별로 계산해 잠시 저장해 둔다 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoldingStopService {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final int MAX_CODES = 40;

    private final StockChartService chartService;
    private final StockAnalysisService analysisService;

    /** stop: 현재가 아래 이탈 기준선 (0이면 계산 불가) */
    public record HoldingStopDto(String stockCode, double stop, String basis,
                                 double target, String targetBasis, String phase, double atr, double planStop,
                                 double ma20, int below20Days, boolean longDown, double longMa,
                                 boolean overheat, double rsi, double dayOpen, double dayLow,
                                 double lastClose, String closeDate, boolean sessionOpen,
                                 double prevClose, double low20) {}

    private record Cached(HoldingStopDto dto, Instant at) {}

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    /** 실패해도 바깥 트랜잭션이 롤백되지 않도록 새 트랜잭션에서 실행 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Map<String, HoldingStopDto> getStops(Collection<String> codes) {
        Map<String, HoldingStopDto> out = new LinkedHashMap<>();
        if (codes == null) return out;
        codes.stream()
                .filter(c -> c != null && !c.isBlank())
                .map(String::trim)
                .distinct()
                .limit(MAX_CODES)
                .forEach(code -> {
                    HoldingStopDto dto = getStop(code);
                    if (dto != null) out.put(code, dto);
                });
        return out;
    }

    /** since(포함) 이후 일봉 종가 중 최고값. 매수 후 최고가(본전 이동 판단)를 화면을 열지 않은 날까지 포함해 계산한다 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public double highestCloseSince(String code, java.time.LocalDate since) {
        if (code == null || since == null) return 0;
        try {
            double max = 0;
            ExchangeSession ses = ExchangeSession.fromCode(code);
            java.time.LocalDate today = sessionOpen(ses) ? java.time.LocalDate.now(ses.zone()) : null;
            for (StockChartDto d : chartService.getDailyChartDtoAsc(code)) {
                if (d.date() == null || d.close() == null || d.date().isBefore(since)) continue;
                if (today != null && d.date().equals(today)) continue;   // 장중 진행 중인 봉은 아직 종가가 아니다
                max = Math.max(max, d.close().doubleValue());
            }
            return max;
        } catch (Exception e) {
            log.warn("[보유 손절선] {} 매수 후 최고가 계산 실패: {}", code, e.getMessage());
            return 0;
        }
    }

    private HoldingStopDto getStop(String code) {
        Cached c = cache.get(code);
        if (c != null && c.at().plus(TTL).isAfter(Instant.now())) return c.dto();
        try {
            HoldingStopDto dto = compute(code);
            cache.put(code, new Cached(dto, Instant.now()));
            return dto;
        } catch (Exception e) {
            // 한 종목이 실패해도 나머지는 보여 준다
            log.warn("[보유 손절선] {} 계산 실패: {}", code, e.getMessage());
            return null;
        }
    }

    private HoldingStopDto compute(String code) {
        List<StockChartDto> daily = chartService.getDailyChartDtoAsc(code);
        List<StockChartDto> weekly = chartService.getWeeklyChartDtoAsc(code);
        MarketContext ctx = MarketContext.of(daily, weekly, ChartDataType.STOCK, ExchangeSession.fromCode(code));

        TradingSignalResult signal = analysisService.getTradingSignal(code, ctx);
        TradingSignalResult.PriceTarget pt = signal.priceTarget();
        double stop = pt != null && Double.isFinite(pt.trailingStop()) ? pt.trailingStop() : 0.0;
        String basis = pt != null && pt.trailingBasis() != null ? pt.trailingBasis() : "";
        double target = pt != null && Double.isFinite(pt.holdTarget()) ? pt.holdTarget() : 0.0;
        String targetBasis = pt != null && pt.holdTargetBasis() != null ? pt.holdTargetBasis() : "";
        String phase = signal.analysis() != null && signal.analysis().phase() != null ? signal.analysis().phase().name() : null;
        double atr = ctx.momentum() != null && Double.isFinite(ctx.momentum().atr()) ? ctx.momentum().atr() : 0.0;
        double plan = pt != null && Double.isFinite(pt.stopLoss()) && pt.stopLoss() > 0 && pt.stopLoss() < ctx.currentPrice()
                ? pt.stopLoss() : 0.0;

        double ma20 = 0; int below20 = 0;
        int n = daily.size();
        if (n >= 20) {
            double sum = 0;
            for (int i = n - 20; i < n; i++) sum += daily.get(i).close().doubleValue();
            ma20 = sum / 20;
            // 날마다 그날의 20일선과 비교해 연속 일수를 센다 (최대 60일까지만 본다)
            for (int end = n - 1; end >= 19 && below20 < 60; end--) {
                double s2 = 0;
                for (int i = end - 19; i <= end; i++) s2 += daily.get(i).close().doubleValue();
                if (daily.get(end).close().doubleValue() < s2 / 20) below20++; else break;
            }
        }
        double longMa = 0;
        if (weekly != null && weekly.size() >= 40) {
            double s3 = 0;
            for (int i = weekly.size() - 40; i < weekly.size(); i++) s3 += weekly.get(i).close().doubleValue();
            longMa = s3 / 40;
        }
        boolean longDown = longMa > 0 && ctx.currentPrice() < longMa;
        boolean overheat = false; double rsi = 0;
        try {
            overheat = com.stocklog.modules.common.finance.indicator.phase.PhaseIndicators.hasOverheat(ctx);
            rsi = ctx.momentum() != null && Double.isFinite(ctx.momentum().rsi()) ? ctx.momentum().rsi() : 0;
        } catch (Exception ignore) { /** 지표가 없으면 과열 정보만 빠진다 */ }
        double dayOpen = n > 0 ? daily.get(n - 1).open().doubleValue() : 0;
        double dayLow = n > 0 ? daily.get(n - 1).low().doubleValue() : 0;
        // 확정 종가: 장중이면 오늘 봉(진행 중)을 건너뛴다
        ExchangeSession ses = ExchangeSession.fromCode(code);
        boolean open = sessionOpen(ses);
        java.time.LocalDate today = java.time.LocalDate.now(ses.zone());
        int ci = n - 1;
        if (open && ci >= 0 && today.equals(daily.get(ci).date())) ci--;
        double lastClose = ci >= 0 ? daily.get(ci).close().doubleValue() : 0;
        String closeDate = ci >= 0 && daily.get(ci).date() != null ? daily.get(ci).date().toString() : null;
        double prevClose = ci >= 1 ? daily.get(ci - 1).close().doubleValue() : 0;
        double low20 = 0;
        for (int i = Math.max(0, ci - 19); i <= ci; i++) {
            double lo = daily.get(i).low().doubleValue();
            if (lo > 0 && (low20 == 0 || lo < low20)) low20 = lo;
        }
        return new HoldingStopDto(code, stop, basis, target, targetBasis, phase, atr, plan,
                ma20, below20, longDown, longMa, overheat, rsi, dayOpen, dayLow, lastClose, closeDate, open,
                prevClose, low20);
    }

    /** 정규장 시간인지 */
    static boolean sessionOpen(ExchangeSession ses) {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(ses.zone());
        java.time.DayOfWeek dw = now.getDayOfWeek();
        if (dw == java.time.DayOfWeek.SATURDAY || dw == java.time.DayOfWeek.SUNDAY) return false;
        java.time.LocalTime t = now.toLocalTime();
        return !t.isBefore(ses.open()) && t.isBefore(ses.open().plusMinutes(ses.minutes()));
    }
}
