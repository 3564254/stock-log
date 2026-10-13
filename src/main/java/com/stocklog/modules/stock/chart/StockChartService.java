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


    // ================ private ================ //

    /** 항상 최신 -> 과거 순. 캐시 히트/미스와 무관하게 순서가 같도록 Asc 결과를 뒤집어 만든다. */
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
                log.info("[Chart] 장 마감 & 최신 데이터 DB 존재. 캐시를 사용합니다. (건수: {})", result.size());
                return result.stream().map(StockChartDto::from).toList();
            }
        }

        return fetchAndSave(code, period, now, stockDto);
    }

    private boolean isMarketOpen(String market) {
        MarketOpenType marketOpen = marketOpenUseCase.isMarketOpen(MarketType.from(market));
        return marketOpen.isOpen();
    }



    // 기존 로직에서 stockDto를 파라미터로 받도록 약간 수정 (DB 중복 조회 방지)
    private List<StockChartDto> fetchAndSave(String code, Period period, LocalDate now, StockDto stockDto) {
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

        // (이전 대화에서 나눴던 Delete & Insert 로직을 여기에 적용하시면 완벽합니다)
        // chartRepository.deleteExistingCharts(...);
        chartRepository.saveAll(newCharts);

        return response.chartDtoList().stream()
                .sorted(Comparator.comparing(StockChartDto::date))
                .toList();
    }
}