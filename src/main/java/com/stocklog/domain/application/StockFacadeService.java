package com.stocklog.domain.application;

import com.stocklog.domain.marketdata.index.dto.IndexDashboardDto;
import com.stocklog.domain.marketdata.index.service.IndexService;
import com.stocklog.domain.marketdata.universe.dto.UniverseDashboardDto;
import com.stocklog.domain.marketdata.universe.service.UniverseService;
import com.stocklog.domain.stock.analysis.domain.signal.EntrySignalAnalyzer;
import com.stocklog.domain.stock.analysis.domain.signal.vo.EntrySignal;
import com.stocklog.domain.application.dto.stock.StockDashBoard;
import com.stocklog.domain.application.dto.stock.StockSnapshot;
import com.stocklog.domain.application.dto.stock.StockSummary;
import com.stocklog.domain.marketdata.universe.entity.Universe;
import com.stocklog.domain.marketdata.universe.dto.UniverseDto;
import com.stocklog.domain.stock.chart.StockChartService;
import com.stocklog.domain.stock.chart.dto.internal.StockChartDto;
import com.stocklog.domain.stock.chart.dto.internal.StockChartListDto;
import com.stocklog.domain.stock.common.type.Period;
import com.stocklog.domain.stock.financial.StockFinancialService;
import com.stocklog.domain.stock.financial.dto.internal.StockFinancialDto;
import com.stocklog.domain.stock.info.StockInfoService;
import com.stocklog.domain.stock.info.dto.internal.StockInfoDto;
import com.stocklog.domain.stock.price.StockPriceService;
import com.stocklog.domain.stock.price.dto.internal.StockPriceDto;
import com.stocklog.domain.stock.stock.StockService;
import com.stocklog.domain.stock.stock.dto.StockDto;
import com.stocklog.domain.stock.valutation.StockValuationService;
import com.stocklog.domain.stock.valutation.dto.internal.StockValuationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockFacadeService {

    private final StockService stockService;
    private final StockInfoService infoService;
    private final StockPriceService priceService;
    private final StockFinancialService financialService;
    private final StockValuationService valuationService;
    private final StockChartService chartService;

    private final EntrySignalAnalyzer entrySignalAnalyzer;

    private final UniverseService universeService;
    private final IndexService indexService;

    public StockSnapshot getStockSnapShot(String code) {
        StockInfoDto infoDto = infoService.fetchInfo(code);
        StockPriceDto priceDto = priceService.fetchPrice(code);
        StockFinancialDto financialDto = financialService.fetchFinancial(code);
        StockValuationDto valuationDto = valuationService.fetchValuation(code);
        List<StockChartDto> dailyCharts = chartService.fetchChartAsc(code, Period.DAY);
        List<StockChartDto> weeklyCharts = chartService.fetchChartAsc(code, Period.MONTH);

        log.info("PEG:{}, FPER:{}, FEPS:{}, TargetPrice:{}",
                valuationDto.peg(),
                valuationDto.fper(),
                valuationDto.feps(),
                valuationDto.targetPrice()
        );

        if (valuationDto.peg().compareTo(BigDecimal.ZERO) == 0 && valuationDto.per() != null && financialDto.epsGrowth() != null) {
            if (financialDto.epsGrowth().compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal peg = valuationDto.per().divide(financialDto.epsGrowth(), 2, RoundingMode.HALF_UP);
                log.info("[Facade] PEG 재계산 로직 - PER: {}, EPS Growth: {} -> 계산된 PEG: {}",
                        valuationDto.per(), financialDto.epsGrowth(), peg);
                valuationDto = valuationService.updatePeg(code, peg, valuationDto);
            } else {
                log.warn("[Facade] EPS Growth가 0이어서 PEG를 계산할 수 없습니다. 종목코드: {}", code);
            }
        }

        return StockSnapshot.builder()
                .info(infoDto)
                .price(priceDto)
                .financial(financialDto)
                .valuation(valuationDto)
                .dailyCharts(dailyCharts)
                .weeklyCharts(weeklyCharts)
                .build();
    }



    public StockSummary getStockSummary(String code) {
        StockInfoDto infoDto = infoService.fetchInfo(code);
        StockPriceDto priceDto = priceService.fetchPrice(code);
        return StockSummary.builder()
                .info(infoDto)
                .price(priceDto)
                .build();
    }

    public StockDashBoard getStockDashBoard(String code) {
        StockDto stock = stockService.getStock(code);
        StockPriceDto priceDto = priceService.fetchPrice(code);
        return StockDashBoard.of(stock, priceDto);
    }



    public UniverseDto getUniverseDto(String code) {
        Optional<UniverseDto> dashboardDto = universeService.getFreshStatus(code);
        if (dashboardDto.isPresent()) {
            return dashboardDto.get();
        }

        // 1. 기본 종목 정보 조회
        StockDto stock = stockService.getStock(code);

        // 2. 분석용 차트 데이터 조회 (반드시 오름차순 - fetchChartAsc 사용)
        // 분석기는 리스트의 마지막 요소를 '오늘'로 인식하여 이평선과 모멘텀을 계산함
        List<StockChartDto> dailyChartDtos = chartService.fetchChartAsc(code, Period.DAY);
        List<StockChartDto> weeklyChartDtos = chartService.fetchChartAsc(code, Period.MONTH);

        // 3. 실시간 가격 정보 조회
        StockPriceDto priceDto = priceService.fetchPrice(code);

        // 4. 전략 분석기 실행
        EntrySignal entrySignal = entrySignalAnalyzer.analyze(dailyChartDtos, weeklyChartDtos);

        // 5. 대시보드 DTO로 변환하여 반환
        UniverseDto result = UniverseDto.builder()
                .code(code)
                .name(stock.name())
                .universe(Universe.getUniverse(code))
                .phase(entrySignal.phase())
                .currentPrice(priceDto.currentPrice())
                .changeRate(priceDto.changeRate())
                .entryPrice(BigDecimal.valueOf(entrySignal.primaryEntry()))
                .build();
        universeService.saveOrUpdate(result);
        return result;
    }

    public List<UniverseDto> getUniverseDashboard() {
        List<String> codes = Universe.getAllCodes();
        List<UniverseDto> result = new ArrayList<>();

        for (String code : codes) {
            try {
                // 1. DB에서 먼저 종목 조회
                Optional<UniverseDto> optionalStatus = universeService.getFreshStatus(code);

                // 2. DB에 데이터가 있고, 업데이트 날짜가 오늘(today)과 같다면 API 호출 생략 (캐시 히트)
                if (optionalStatus.isPresent()) {
                    UniverseDto status = optionalStatus.get();
                        result.add(status); // 엔티티 -> DTO 변환 메서드 필요
                        continue; // 다음 종목으로 넘어감 (sleep 안 함)
                }

                // 3. DB에 없거나, 업데이트 날짜가 어제 이전(캐시 미스)인 경우 API 호출
                log.info("{} 종목 최신 데이터 API 호출 및 갱신", code);
                UniverseDto freshDto = getUniverseDto(code);

                universeService.saveOrUpdate(freshDto);
                result.add(freshDto);

                // 5. API를 호출했을 때만 초당 호출 제한 방지를 위해 Sleep 적용
                Thread.sleep(1000);

            } catch (InterruptedException e) {
                log.error("API 호출 대기 중 인터럽트 발생: {}", e.getMessage());
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("{} 종목 데이터 처리 중 에러: {}", code, e.getMessage());
            }
        }

        return result;
    }

    public UniverseDashboardDto getUniverseDashboardDto() {
        return UniverseDashboardDto.builder()
                .xlk(getUniverseDto("XLK"))
                .xlp(getUniverseDto("XLP"))
                .xli(getUniverseDto("XLI"))
                .xle(getUniverseDto("XLE"))
                .sp500(getUniverseDto("QQQ"))
                .sp500(getUniverseDto("SPY"))
                .build();
    }

    public IndexDashboardDto getMarketDashboard() {
        return indexService.getMarketDashboard();
    }


    public StockChartListDto getChartList(String code, Period period) {
        List<StockChartDto> stockChartList = chartService.fetchChartDesc(code, period);
        return StockChartListDto.builder()
                .code(code)
                .chartDtoList(stockChartList)
                .period(period)
                .build();
    }

    public List<StockSummary> getStockstockSummariesByKeword(String keyword, Pageable pageable) {
        List<StockSummary> stockSummaries = new ArrayList<>();
        List<StockInfoDto> stockInfoDtos = infoService.fetchinfoByKeyword(keyword, pageable);
        for (StockInfoDto infoDto : stockInfoDtos) {
            StockPriceDto priceDto = priceService.fetchPrice(infoDto.code());
            stockSummaries.add(StockSummary.builder().info(infoDto).price(priceDto).build());
        }
        return stockSummaries;
    }

    public List<StockDashBoard> getStockDashBoardsByKeyword(String keyword, Pageable pageable) {
        List<StockDashBoard> dashBoards = new ArrayList<>();
        List<StockDto> stockDtos = stockService.getStockByKeword(keyword, pageable);
        for (StockDto stockDto : stockDtos) {
            StockPriceDto priceDto = priceService.fetchPrice(stockDto.code());
            dashBoards.add(StockDashBoard.of(stockDto, priceDto));
        }
        return dashBoards;
    }

    public StockPriceDto getPrice(String code) {
        return priceService.fetchPrice(code);
    }

    public StockInfoDto getInfo(String code) {
        return infoService.fetchInfo(code);
    }

    public StockFinancialDto getFinaincial(String code) {
        return financialService.fetchFinancial(code);
    }

    public StockValuationDto getValutaion(String code) {
        return valuationService.fetchValuation(code);
    }
}