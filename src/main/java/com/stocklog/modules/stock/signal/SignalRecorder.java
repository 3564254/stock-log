package com.stocklog.modules.stock.signal;

import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.common.finance.model.ExchangeSession;
import com.stocklog.modules.common.type.Nation;
import com.stocklog.modules.stock.analysis.MarketRegimeService;
import com.stocklog.modules.stock.analysis.signal.trading.TradingSignalResult;
import com.stocklog.modules.stock.chart.StockChartService;
import com.stocklog.modules.stock.chart.dto.StockChartDto;
import com.stocklog.modules.stock.momentum.MomentumService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 스크리닝이 분석한 종목을 신호 기록(SignalRecord)으로 남긴다 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignalRecorder {

    private final SignalRecordRepository repository;
    private final StockChartService chartService;
    private final MarketRegimeService marketRegimeService;
    private final MomentumService momentumService;

    private record IndexCache(LocalDate day, List<StockChartDto> daily) {}
    private final Map<Nation, IndexCache> indexCache = new ConcurrentHashMap<>();

    /** 기록할 수 없으면 null (장중, 데이터 부족) */
    public SignalRecord snapshot(Nation nation, String code, List<StockChartDto> daily, List<StockChartDto> weekly,
                                 MarketContext ctx, TradingSignalResult signal, LocalDateTime analyzedAt) {
        try {
            ExchangeSession ses = ExchangeSession.fromCode(code);
            if (sessionOpen(ses) || daily == null || daily.size() < 21) return null;
            StockChartDto last = daily.get(daily.size() - 1);
            if (last.date() == null || last.close() == null) return null;
            double close = last.close().doubleValue();

            double ma20 = 0;
            for (int i = daily.size() - 20; i < daily.size(); i++) ma20 += daily.get(i).close().doubleValue();
            ma20 /= 20;
            Double ma40 = null, ma40p = null;
            if (weekly != null && weekly.size() >= 44) { ma40 = avg(weekly, weekly.size() - 40, weekly.size()); ma40p = avg(weekly, weekly.size() - 44, weekly.size() - 4); }
            Boolean trendUp = ma40 == null ? null : close > ma40 && ma40 > ma40p;
            Double ret126 = daily.size() > 126 ? close / daily.get(daily.size() - 1 - 126).close().doubleValue() - 1 : null;
            Double idx126 = ret126 == null ? null : indexRet(nation, daily.get(daily.size() - 1 - 126).date(), last.date());
            Boolean weak = null;
            try { MarketRegimeService.MarketRegime m = marketRegimeService.forStock(code); if (m != null && m.known()) weak = m.weak(); } catch (Exception ignore) {}
            Double moPct = null;
            if (nation == Nation.US) {
                try { MomentumService.MomentumView v = momentumService.view(code); if (v != null && v.ranked()) moPct = v.pct(); } catch (Exception ignore) {}
            }
            double atr = ctx.momentum() != null && Double.isFinite(ctx.momentum().atr()) ? ctx.momentum().atr() : 0;
            TradingSignalResult.PriceTarget pt = signal.priceTarget();
            TradingSignalResult.EntryPlan plan = signal.entryPlan();
            String headline = signal.narrative() != null ? signal.narrative().headline() : null;
            if (headline != null && headline.length() > 200) headline = headline.substring(0, 199) + "…";
            return SignalRecord.builder()
                    .recordedAt(analyzedAt).signalDate(last.date()).stockCode(code).nation(nation)
                    .verdict(signal.verdict()).entryType(plan != null && plan.type() != null ? plan.type().name() : null)
                    .chartPhase(signal.analysis() != null && signal.analysis().phase() != null ? signal.analysis().phase().name() : null)
                    .headline(headline).close(close).atr(atr).ma20(ma20).ma40w(ma40).trendUp(trendUp)
                    .ret126(ret126).indexRet126(idx126).marketWeak(weak).momentumPct(moPct)
                    .planEntry(pt != null ? pt.primaryEntry() : 0).planStop(pt != null ? pt.stopLoss() : 0)
                    .planTarget(pt != null ? pt.primaryTarget() : 0).riskReward(plan != null ? plan.riskReward() : 0)
                    .tradeRiskPct(signal.risk() != null ? signal.risk().tradeRiskPercent() : null)
                    .recWeight(signal.risk() != null ? signal.risk().recommendedWeight() : null)
                    .build();
        } catch (Exception e) {
            log.warn("[신호 기록] {} 스냅샷 실패: {}", code, e.getMessage());
            return null;
        }
    }

    /** 같은 종목·신호일·엔진 버전이 이미 있으면 건너뛴다 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int saveAll(List<SignalRecord> records) {
        int n = 0;
        for (SignalRecord r : records) {
            if (r == null || repository.existsByStockCodeAndSignalDateAndEngineVersion(r.getStockCode(), r.getSignalDate(), r.getEngineVersion())) continue;
            repository.save(r); n++;
        }
        return n;
    }

    private Double indexRet(Nation nation, LocalDate from, LocalDate to) {
        String proxy = nation == Nation.KR ? "069500" : "SPY";
        LocalDate today = LocalDate.now();
        IndexCache c = indexCache.get(nation);
        if (c == null || !today.equals(c.day())) {
            try { c = new IndexCache(today, chartService.getDailyChartDtoAsc(proxy)); indexCache.put(nation, c); }
            catch (Exception e) { return null; }
        }
        Double a = null, b = null;
        for (StockChartDto d : c.daily()) {
            if (d.date() == null || d.close() == null) continue;
            if (a == null && !d.date().isBefore(from)) a = d.close().doubleValue();
            if (!d.date().isAfter(to)) b = d.close().doubleValue();
        }
        return a == null || b == null || a <= 0 ? null : b / a - 1;
    }

    private static double avg(List<StockChartDto> w, int from, int to) {
        double s = 0; for (int i = from; i < to; i++) s += w.get(i).close().doubleValue(); return s / (to - from);
    }

    static boolean sessionOpen(ExchangeSession ses) {
        ZonedDateTime now = ZonedDateTime.now(ses.zone());
        DayOfWeek dw = now.getDayOfWeek();
        if (dw == DayOfWeek.SATURDAY || dw == DayOfWeek.SUNDAY) return false;
        LocalTime t = now.toLocalTime();
        return !t.isBefore(ses.open()) && t.isBefore(ses.open().plusMinutes(ses.minutes()));
    }
}
