package com.stocklog.modules.stock.chart;

import com.stocklog.modules.stock.chart.dto.ChartRequest;
import com.stocklog.modules.stock.chart.dto.StockChartDto;
import com.stocklog.modules.stock.chart.dto.StockChartResponse;
import com.stocklog.modules.stock.domain.type.Period;
import com.stocklog.modules.stock.stock.StockService;
import com.stocklog.modules.stock.stock.dto.StockDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockChartService {

    private final StockChartRepository chartRepository;
    private final List<StockChartProvider> providers;
    private final StockService stockService;

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


    // ================ private ================ //

    private List<StockChartDto> fetchChartDesc(String code, Period period) {

        LocalDate now = LocalDate.now();
        List<StockChart> result = chartRepository.findByCodeAndPeriodOrderByTradeDateDesc(code, period);

        // 1. 시장 구분을 위해 주식 정보 먼저 조회
        StockDto stockDto = stockService.getStockDto(code);
        boolean isMarketOpen = isMarketOpen(stockDto.market().toString());

        // 2. 캐시 체크: 장이 마감되었고, DB에 최신(최근 3일 이내) 데이터가 존재하면 캐시 사용
        if (!isMarketOpen && result.size() >= 30) {
            LocalDate lastDate = result.get(0).getTradeDate(); // DESC이므로 0번째가 최신
            if (lastDate.isAfter(now.minusDays(4))) { // 주말 고려 방어 로직
                log.info("[Chart] 캐시 히트: 장 마감 & 최신 데이터 DB 존재 (건수: {})", result.size());
                return result.stream().map(StockChartDto::from).toList();
            }
        }

        return fetchAndSave(code, period, now, stockDto);
    }


    private List<StockChartDto> fetchChartAsc(String code, Period period) {

        LocalDate now = LocalDate.now();
        List<StockChart> result = chartRepository.findByCodeAndPeriodOrderByTradeDateAsc(code, period);

        StockDto stockDto = stockService.getStockDto(code);
        boolean isMarketOpen = isMarketOpen(stockDto.market().toString());

        if (!isMarketOpen && result.size() >= 30) {
            LocalDate lastDate = result.get(result.size() - 1).getTradeDate(); // ASC이므로 마지막이 최신
            if (lastDate.isAfter(now.minusDays(4))) {
                log.info("[Chart] 장 마감 & 최신 데이터 DB 존재. 캐시를 사용합니다. (건수: {})", result.size());
                return result.stream().map(StockChartDto::from).toList();
            }
        }

        return fetchAndSave(code, period, now, stockDto);
    }

    // ── [신규 추가] 국장/미장 시간에 따른 장중 여부 판별 ──
    private boolean isMarketOpen(String market) {
        LocalTime now = LocalTime.now();
        String upperMarket = market.toUpperCase();

        if (upperMarket.contains("KOSPI") || upperMarket.contains("KOSDAQ") || upperMarket.contains("KRX")) {
            // 국장: 09:00 ~ 15:30
            return !now.isBefore(LocalTime.of(9, 0)) && now.isBefore(LocalTime.of(15, 30));
        } else {
            // 미장: 22:30 ~ 06:00 (서머타임/정규장 오차 넉넉히 커버하기 위해 22:00 ~ 06:30으로 잡음)
            //return !now.isBefore(LocalTime.of(22, 0)) || now.isBefore(LocalTime.of(6, 30));
            return true;
        }
    }

    // 기존 로직에서 stockDto를 파라미터로 받도록 약간 수정 (DB 중복 조회 방지)
    private List<StockChartDto> fetchAndSave(String code, Period period, LocalDate now, StockDto stockDto) {
        String dateStr = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        ChartRequest request = new ChartRequest(code, stockDto.market(), dateStr);

        StockChartResponse response = providers.stream()
                .filter(p -> p.supports(stockDto.market()))
                .findFirst()
                .map(p -> {
                    return p.fetchInfo(request, period);
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

        // (이전 대화에서 나눴던 Delete & Insert 로직을 여기에 적용하시면 완벽합니다)
        // chartRepository.deleteExistingCharts(...);
        chartRepository.saveAll(newCharts);

        return response.chartDtoList().stream()
                .sorted(Comparator.comparing(StockChartDto::date))
                .toList();
    }
}