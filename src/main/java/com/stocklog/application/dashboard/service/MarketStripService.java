package com.stocklog.application.dashboard.service;

import com.stocklog.application.dashboard.MarketStripResponse;
import com.stocklog.application.dashboard.MarketStripResponse.Item;
import com.stocklog.application.dashboard.MarketStripResponse.Regime;
import com.stocklog.modules.market.analysis.dto.MarketRegimeDto;
import com.stocklog.modules.market.analysis.service.MarketAnalysisService;
import com.stocklog.modules.market.domain.type.IndexType;
import com.stocklog.modules.market.exchange.dto.ExchangeRateInfo;
import com.stocklog.modules.market.index.dto.IndexChartDto;
import com.stocklog.modules.market.index.dto.IndexDto;
import com.stocklog.modules.market.service.ExchangeService;
import com.stocklog.modules.market.service.IndexService;
import com.stocklog.modules.stock.chart.StockChartService;
import com.stocklog.modules.stock.chart.dto.StockChartDto;
import com.stocklog.modules.stock.price.StockPriceService;
import com.stocklog.modules.stock.price.dto.StockPriceDto;
import com.stocklog.modules.stock.screening.ScreeningService;
import com.stocklog.modules.stock.screening.dto.ScreeningCandidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/** 홈 상단 지수 띠 조합 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketStripService {

    private static final Duration STRIP_TTL = Duration.ofSeconds(60);
    private static final Duration REGIME_TTL = Duration.ofMinutes(10);
    private static final int SPARK_SIZE = 30;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 띠 순서. source: FRED 지수 seriesId 또는 ETF 시세 */
    private enum Source { FRED, ETF, FX }

    private record Def(String key, String name, Source source, String seriesId, String etfCode, String unit) {}

    private static final List<Def> DEFS = List.of(
            new Def("SP500", "S&P 500", Source.FRED, IndexType.SP500.getSeriesId(), "SPY", "PT"),
            new Def("NDX", "나스닥 100", Source.FRED, IndexType.NASDAQ_100.getSeriesId(), "QQQ", "PT"),
            new Def("DOW", "다우", Source.FRED, IndexType.DOW_JONES.getSeriesId(), "DIA", "PT"),
            new Def("SOX", "반도체", Source.FRED, IndexType.PHLX_SEMI.getSeriesId(), "SOXX", "PT"),
            new Def("RUT", "러셀2000", Source.ETF, null, "IWM", "USD"),
            new Def("SCHD", "배당성장", Source.ETF, null, "SCHD", "USD"),
            new Def("VIX", "VIX", Source.FRED, IndexType.VIX.getSeriesId(), null, "PT"),
            new Def("KOSPI200", "코스피200", Source.ETF, null, "069500", "KRW"),
            new Def("KOSDAQ150", "코스닥150", Source.ETF, null, "229200", "KRW"),
            new Def("USDKRW", "달러/원", Source.FX, null, null, "FX")
    );

    private final MarketAnalysisService marketAnalysisService;
    private final IndexService indexService;
    private final ExchangeService exchangeService;
    private final StockPriceService priceService;
    private final StockChartService chartService;
    private final ScreeningService screeningService;

    private volatile Cached<MarketStripResponse> stripCache;
    private volatile Cached<Regime> regimeCache;

    private record Cached<T>(Instant at, T value) {
        boolean fresh(Duration ttl) { return at.plus(ttl).isAfter(Instant.now()); }
    }

    public MarketStripResponse getStrip() {
        Cached<MarketStripResponse> c = stripCache;
        if (c != null && c.fresh(STRIP_TTL)) return c.value();

        Map<String, ScreeningCandidate> etfAnalysis = safeEtfAnalysis();
        List<Item> items = new ArrayList<>();
        for (Def def : DEFS) {
            items.add(buildItem(def, etfAnalysis.get(def.etfCode())));
        }
        MarketStripResponse strip = new MarketStripResponse(getRegime(), items, LocalDateTime.now(KST));
        stripCache = new Cached<>(Instant.now(), strip);
        return strip;
    }

    // 시장 국면

    private Regime getRegime() {
        Cached<Regime> c = regimeCache;
        if (c != null && c.fresh(REGIME_TTL)) return c.value();
        try {
            MarketRegimeDto dto = marketAnalysisService.analyzeMarket();
            Regime regime = dto == null ? null
                    : new Regime(dto.regime(), dto.risk(), dto.trendLevel(), dto.regimeDescription(), dto.confidence());
            regimeCache = new Cached<>(Instant.now(), regime);
            return regime;
        } catch (Exception e) {
            log.warn("[Strip] 시장 국면 계산 실패: {}", e.getMessage());
            return c != null ? c.value() : null;   // 실패하면 조금 오래된 판정이라도
        }
    }

    // 지수 한 칸

    private Item buildItem(Def def, ScreeningCandidate etf) {
        try {
            return switch (def.source()) {
                case FRED -> fredItem(def, etf);
                case ETF -> etfItem(def, etf);
                case FX -> fxItem(def);
            };
        } catch (Exception e) {
            log.warn("[Strip] {} 조회 실패: {}", def.key(), e.getMessage());
            return item(def, null, null, null, List.of(), null, etf);
        }
    }

    /** FRED 일별 종가 (DB) */
    private Item fredItem(Def def, ScreeningCandidate etf) {
        IndexDto dto = indexService.getIndexDtoFromDb(def.seriesId());
        List<IndexChartDto> data = dto.data() == null ? List.of() : dto.data().stream()
                .filter(d -> d.date() != null && d.close() != null && d.close().signum() > 0)
                .toList();
        if (data.isEmpty()) return item(def, null, null, null, List.of(), null, etf);

        IndexChartDto last = data.get(data.size() - 1);
        List<BigDecimal> spark = data.subList(Math.max(0, data.size() - SPARK_SIZE), data.size()).stream()
                .map(IndexChartDto::close).toList();
        return item(def, last.close(), dto.changeRate(), null, spark, last.date(), etf);
    }

    /** 지수 ETF 시세 (KIS) + 일봉(DB에 쌓인 차트) */
    private Item etfItem(Def def, ScreeningCandidate etf) {
        StockPriceDto price = priceService.fetchPrice(def.etfCode());
        BigDecimal value = price != null && price.currentPrice() != null && price.currentPrice().signum() > 0
                ? price.currentPrice() : (etf != null ? etf.currentPrice() : null);
        BigDecimal rate = price != null ? price.changeRate() : null;

        List<BigDecimal> spark = List.of();
        try {
            List<StockChartDto> daily = chartService.getDailyChartDtoAsc(def.etfCode());
            spark = daily.subList(Math.max(0, daily.size() - SPARK_SIZE), daily.size()).stream()
                    .map(StockChartDto::close)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.debug("[Strip] {} 일봉 조회 실패: {}", def.etfCode(), e.getMessage());
        }
        return item(def, value, rate, null, spark, null, etf);
    }

    /** 환율: 등락률은 없고 방향만 있다 */
    private Item fxItem(Def def) {
        ExchangeRateInfo fx = exchangeService.fetchExchangeRate();
        BigDecimal value = fx != null && fx.baseRate() != null && fx.baseRate().signum() > 0 ? fx.baseRate() : null;
        return item(def, value, null, fx != null ? direction(fx.rateChangeType()) : null, List.of(), null, null);
    }

    private static Item item(Def def, BigDecimal value, BigDecimal rate, String direction,
                             List<BigDecimal> spark, LocalDate asOf, ScreeningCandidate etf) {
        if (direction == null && rate != null) {
            direction = rate.signum() > 0 ? "UP" : rate.signum() < 0 ? "DOWN" : "FLAT";
        }
        String link = def.etfCode() != null ? "/stock/analysis?code=" + def.etfCode() : null;
        return new Item(def.key(), def.name(), def.etfCode(), value, rate, direction, def.unit(), spark, asOf,
                def.etfCode(),
                etf != null ? etf.chartPhase() : null,
                etf != null && etf.verdict() != null ? etf.verdict().name() : null,
                etf != null ? etf.verdictLabel() : null,
                link);
    }

    private static String direction(String rateChangeType) {
        if (rateChangeType == null) return null;
        String t = rateChangeType.trim().toUpperCase(Locale.ROOT);
        if (t.equals("UP") || t.equals("RISE") || t.equals("INCREASE")) return "UP";
        if (t.equals("DOWN") || t.equals("FALL") || t.equals("DECREASE")) return "DOWN";
        return "FLAT";
    }

    private Map<String, ScreeningCandidate> safeEtfAnalysis() {
        try {
            return screeningService.getIndexEtfAnalysis();
        } catch (Exception e) {
            log.warn("[Strip] 지수 ETF 분석 결과 조회 실패: {}", e.getMessage());
            return Map.of();
        }
    }
}
