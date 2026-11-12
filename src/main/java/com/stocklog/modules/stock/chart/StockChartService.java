package com.stocklog.modules.stock.chart;

import com.stocklog.modules.market.application.usecase.IsMarketOpenUseCase;
import com.stocklog.modules.market.open.MarketOpenType;
import com.stocklog.modules.stock.chart.dto.ChartRequest;
import com.stocklog.modules.stock.chart.dto.StockChartDto;
import com.stocklog.modules.stock.chart.dto.StockChartResponse;
import com.stocklog.modules.common.type.Period;
import com.stocklog.modules.stock.domain.type.MarketType;
import com.stocklog.modules.stock.stock.StockService;
import com.stocklog.modules.stock.stock.dto.StockDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockChartService {

    private final StockChartRepository chartRepository;
    private final List<StockChartProvider> providers;
    private final StockService stockService;

    private final IsMarketOpenUseCase marketOpenUseCase;

    @Transactional
    public List<StockChartDto> getDailyChartDtoDesc(String code) {
        return fetchChartDesc(code, Period.DAY);
    }

    @Transactional
    public List<StockChartDto> getDailyChartDtoAsc(String code) {
        return fetchChartAsc(code, Period.DAY);
    }

    @Transactional
    public List<StockChartDto> getWeeklyChartDtoDesc(String code) {
        return fetchChartDesc(code, Period.WEEK);
    }

    @Transactional
    public List<StockChartDto> getWeeklyChartDtoAsc(String code) {
        return fetchChartAsc(code, Period.WEEK);
    }

    @Transactional
    public StockChartResponse getChartListDto(String code, Period period) {
        List<StockChartDto> stockChartList = fetchChartDesc(code, period);
        return StockChartResponse.builder()
                .code(code)
                .chartDtoList(stockChartList)
                .period(period)
                .build();
    }

    /** 항상 최신 -> 과거 순. 캐시 히트/미스와 무관하게 순서가 같도록 Asc 결과를 뒤집어 만든다 */
    private List<StockChartDto> fetchChartDesc(String code, Period period) {
        List<StockChartDto> desc = new ArrayList<>(fetchChartAsc(code, period));
        Collections.reverse(desc);
        return desc;
    }

    private List<StockChartDto> fetchChartAsc(String code, Period period) {

        LocalDate now = LocalDate.now();
        List<StockChart> result = chartRepository.findByCodeAndPeriodOrderByTradeDateAsc(code, period);

        StockDto stockDto = stockService.getStockDto(code);
        boolean isMarketOpen = isMarketOpen(stockDto.market().toString());

        if (!isMarketOpen && result.size() >= 30) {
            LocalDate lastDate = result.get(result.size() - 1).getTradeDate(); // ASC이므로 마지막이 최신
            if (lastDate.isAfter(now.minusDays(4))) {
                // 저장된 봉 안에 분할처럼 하루에 1/k · k배로 뛴 자리가 있으면, 하루 한 번은 API로 다시 받아 기준을 맞춰 본다
                if (hasSplitLikeJump(result) && splitChecked.add(code + ":" + period + ":" + now)) {
                    log.info("[Chart] {} {} 저장 봉에 분할 같은 점프가 있어 다시 받아 확인합니다", code, period);
                    return fetchAndSave(code, period, now, stockDto, result);
                }
                log.info("[Chart] 장 마감 & 최신 데이터 DB 존재. 캐시를 사용합니다. (건수: {})", result.size());
                return result.stream().map(StockChartDto::from).toList();
            }
        }

        return fetchAndSave(code, period, now, stockDto, result);
    }

    /** 일봉을 캐시와 상관없이 API에서 다시 받아 저장한다 */
    public void refreshDaily(String code) { refresh(code, Period.DAY); }

    /** 캐시와 상관없이 다시 받아 저장 (일봉 · 주봉) */
    public void refresh(String code, Period period) {
        List<StockChart> stored = chartRepository.findByCodeAndPeriodOrderByTradeDateAsc(code, period);
        fetchAndSave(code, period, LocalDate.now(), stockService.getStockDto(code), stored);
    }

    private boolean isMarketOpen(String market) {
        MarketOpenType marketOpen = marketOpenUseCase.isMarketOpen(MarketType.from(market));
        return marketOpen.isOpen();
    }

    /** 저장된 봉과 방금 받은 봉이 겹치는 날짜의 종가 비율 = 새 / 옛 */
    /** factor: 곱할 비율, before: 이 날짜보다 앞의 저장 봉에만 적용 */
    record Adj(BigDecimal factor, LocalDate before) {}

    static Adj adjustFactor(List<StockChart> stored, List<StockChartDto> fresh) {
        if (stored == null || stored.isEmpty() || fresh == null || fresh.size() < 4) return null;
        java.util.Map<LocalDate, BigDecimal> old = new java.util.HashMap<>();
        stored.forEach(c -> { if (c.getClosePrice() != null && c.getClosePrice().signum() > 0) old.put(c.getTradeDate(), c.getClosePrice()); });
        LocalDate last = fresh.stream().map(StockChartDto::date).filter(java.util.Objects::nonNull).max(LocalDate::compareTo).orElse(null);
        List<Double> ratios = new ArrayList<>();
        for (StockChartDto d : fresh) {
            if (d.date() == null || d.date().equals(last) || d.close() == null || d.close().signum() <= 0) continue;
            BigDecimal o = old.get(d.date());
            if (o != null) ratios.add(d.close().doubleValue() / o.doubleValue());
        }
        if (ratios.size() >= 3) {
            Collections.sort(ratios);
            double med = ratios.get(ratios.size() / 2);
            long agree = ratios.stream().filter(r -> Math.abs(r / med - 1) <= 0.005).count();
            LocalDate firstFresh = fresh.stream().map(StockChartDto::date).filter(java.util.Objects::nonNull).min(LocalDate::compareTo).orElse(null);
            if (Math.abs(med - 1) > 0.01 && agree >= ratios.size() * 0.9) return new Adj(BigDecimal.valueOf(med), firstFresh);
            if (Math.abs(med - 1) <= 0.01 && agree >= ratios.size() * 0.9) return boundaryFactor(stored, fresh);   // 겹치는 구간은 이미 새 기준
            return null;
        }
        return boundaryFactor(stored, fresh);
    }

    /** 오늘 이미 분할 확인을 한 종목 · 주기 (같은 날 반복 호출 방지) */
    private final java.util.Set<String> splitChecked = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 연속한 두 봉의 종가가 1/k 또는 k배로 뛴 자리가 있는지 */
    static boolean hasSplitLikeJump(List<StockChart> asc) {
        for (int i = 1; i < asc.size(); i++) {
            BigDecimal a = asc.get(i - 1).getClosePrice(), b = asc.get(i).getClosePrice();
            if (a == null || b == null || a.signum() <= 0 || b.signum() <= 0) continue;
            double j = b.doubleValue() / a.doubleValue();
            if (Math.abs(j - 1) <= 0.25) continue;
            for (int k : SPLIT_K) if (Math.abs(j * k - 1) <= 0.10 || Math.abs(j / k - 1) <= 0.10) return true;
        }
        return false;
    }

    /** 흔한 분할 · 병합 비율 */
    private static final int[] SPLIT_K = {2, 3, 4, 5, 6, 8, 10, 15, 20, 25, 30, 40, 50, 100};

    /** 저장 봉과 새로 받은 봉 경계에서 분할 비율만큼 뛴 흔적이 있으면 그 비율 */
    static Adj boundaryFactor(List<StockChart> stored, List<StockChartDto> fresh) {
        // 받은 구간이 시작하는 날(또는 그 이전) 까지의 저장 봉에서 가장 늦은 분할 같은 점프를 찾는다
        LocalDate firstFresh = fresh.stream().map(StockChartDto::date).filter(java.util.Objects::nonNull).min(LocalDate::compareTo).orElse(null);
        if (firstFresh == null) return null;
        List<StockChart> asc = stored.stream().filter(c -> c.getClosePrice() != null && c.getClosePrice().signum() > 0)
                .sorted(Comparator.comparing(StockChart::getTradeDate)).toList();
        for (int i = asc.size() - 1; i >= 1; i--) {
            StockChart a = asc.get(i - 1), b = asc.get(i);
            if (b.getTradeDate().isAfter(firstFresh)) continue;        // 받은 구간 안의 점프는 제공처 값 그대로 (진짜 급등락일 수 있음)
            double jump = b.getClosePrice().doubleValue() / a.getClosePrice().doubleValue();
            if (Math.abs(jump - 1) <= 0.25) continue;
            // 최근 60일 안의 점프는 허용 오차를 넓게 본다
            double tol = b.getTradeDate().isAfter(firstFresh.minusDays(60)) ? 0.10 : 0.04;
            for (int k : SPLIT_K) {
                if (Math.abs(jump * k - 1) <= tol) return new Adj(BigDecimal.ONE.divide(BigDecimal.valueOf(k), java.math.MathContext.DECIMAL64), b.getTradeDate());   // k:1 분할
                if (Math.abs(jump / k - 1) <= tol) return new Adj(BigDecimal.valueOf(k), b.getTradeDate());                                                          // 1:k 병합
            }
            return null;   // 가장 늦은 큰 점프가 분할 비율이 아니면 손대지 않는다
        }
        return null;
    }

    // 기존 로직에서 stockDto를 파라미터로 받도록 약간 수정 (DB 중복 조회 방지)
    private List<StockChartDto> fetchAndSave(String code, Period period, LocalDate now, StockDto stockDto,
                                             List<StockChart> stored) {
        String dateStr = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        ChartRequest request = new ChartRequest(code, stockDto.market(), dateStr);

        StockChartResponse response = providers.stream()
                .filter(p -> p.supports(stockDto.market()))
                .findFirst()
                .map(p -> {
                    return p.fetchChart(request, period);
                })
                .orElseThrow(() -> {
                    log.error("[Chart] 지원하지 않는 시장입니다: {}", stockDto.market());
                    return new IllegalArgumentException("지원하지 않는 시장입니다.");
                });

        List<StockChart> newCharts = response.chartDtoList().stream()
                .map(data -> StockChart.builder()
                        .code(code)
                        .tradeDate(data.date())
                        .openPrice(data.open())
                        .highPrice(data.high())
                        .lowPrice(data.low())
                        .closePrice(data.close())
                        .volume(data.volume())
                        .period(period)
                        .build())
                .toList();

        Adj adj = adjustFactor(stored, response.chartDtoList());
        if (adj != null && adj.factor() != null && adj.before() != null) {
            List<StockChart> old = stored.stream().filter(c -> c.getTradeDate().isBefore(adj.before())).toList();
            old.forEach(c -> c.rescale(adj.factor()));
            chartRepository.saveAll(old);
            log.warn("[Chart] {} {} 수정주가 기준 변경 감지 (분할·병합 등): {} 이전 봉 {}개를 ×{} 로 맞춤", code, period, adj.before(), old.size(), adj.factor());
        } else if (hasSplitLikeJump(stored)) {
            log.info("[Chart] {} {} 분할 같은 점프가 있지만 받은 구간 안이거나 비율이 맞지 않아 그대로 둠", code, period);
        }
        chartRepository.saveAll(newCharts);

        // 장중(API 경로)에도 장 마감 후(DB 캐시 경로)와 같은 데이터를 쓰도록 DB에 쌓인 과거 봉 + 방금 받은 봉을 합친다
        java.util.TreeMap<LocalDate, StockChartDto> merged = new java.util.TreeMap<>();
        stored.forEach(c -> merged.put(c.getTradeDate(), StockChartDto.from(c)));
        response.chartDtoList().forEach(d -> merged.put(d.date(), d));
        return List.copyOf(merged.values());
    }
}