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

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 보유 종목의 '보유 기준 손절선'(타점 엔진의 holdingStop)을 종목별로 계산해 잠시 저장해 둔다.
 *
 * 장중에는 차트를 매번 증권사 API에서 새로 받으므로, 포트폴리오를 열 때마다 모든 보유 종목을 다시 계산하면
 * 화면이 느려지고 호출 한도에 걸릴 수 있다. 손절선은 일봉 구조로 정해져 하루 안에 자주 바뀌지 않으므로 30분간 재사용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoldingStopService {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final int MAX_CODES = 40;

    private final StockChartService chartService;
    private final StockAnalysisService analysisService;

    /**
     * stop: 현재가 아래 이탈 기준선 (0이면 계산 불가) — 평단보다 아래면 '손절선', 위면 '수익 보호선'으로 화면에서 부른다
     * target: 현재가 위 가장 가까운 저항 = 익절 목표 (0이면 위쪽 저항 없음)
     */
    public record HoldingStopDto(String stockCode, double stop, String basis,
                                 double target, String targetBasis, String phase) {}

    private record Cached(HoldingStopDto dto, Instant at) {}

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

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

    private HoldingStopDto getStop(String code) {
        Cached c = cache.get(code);
        if (c != null && c.at().plus(TTL).isAfter(Instant.now())) return c.dto();
        try {
            HoldingStopDto dto = compute(code);
            cache.put(code, new Cached(dto, Instant.now()));
            return dto;
        } catch (Exception e) {
            // 한 종목이 실패해도 나머지는 보여 준다. 실패는 저장하지 않아 다음 요청에 다시 시도한다
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
        return new HoldingStopDto(code, stop, basis, target, targetBasis, phase);
    }
}
