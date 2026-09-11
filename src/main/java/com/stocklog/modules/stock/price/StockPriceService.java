package com.stocklog.modules.stock.price;

import com.stocklog.modules.stock.application.request.StockRequest;
import com.stocklog.modules.stock.price.dto.StockPriceDto;
import com.stocklog.modules.stock.price.dto.StockPriceResponse;
import com.stocklog.modules.stock.stock.StockService;
import com.stocklog.modules.stock.stock.dto.StockDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
@Service
@RequiredArgsConstructor
@Slf4j
public class StockPriceService {

    private final List<StockPriceProvider> providers;
    private final StockService stockService;

    public StockPriceDto fetchPrice(String code) {
        StockDto stock = stockService.getStockDto(code);
        StockPriceResponse response = providers.stream()
                .filter(provider -> provider.supports(stock.market()))
                .findFirst()
                .map(provider -> provider.fetchPrice(StockRequest.from(stock)))
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 시장이거나 존재하지 않는 종목입니다: " + code));
        return StockPriceDto.from(response);
    }

    // 다건 조회 (외부 API 비동기 병렬 호출)
    public Map<String, StockPriceDto> fetchPricesInBulk(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Collections.emptyMap();
        }

        // 1. 각 종목코드마다 독립적인 비동기 스레드 작업 생성 (동시 실행)
        List<CompletableFuture<Map.Entry<String, StockPriceDto>>> futures = codes.stream()
                .map(code -> CompletableFuture.supplyAsync(() -> {
                    try {
                        // 기존에 만들어둔 단건 조회 로직을 그대로 재사용
                        StockPriceDto priceDto = fetchPrice(code);
                        return Map.entry(code, priceDto);
                    } catch (Exception e) {
                        // 특정 종목의 API 통신이 실패해도 전체 로직이 터지지 않도록 방어
                        log.error("[StockPriceService] {} 외부 API 가격 조회 실패: {}", code, e.getMessage());
                        return null;
                    }
                }))
                .toList();

        // 2. 파생된 모든 비동기 작업이 완료될 때까지 메인 스레드 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 3. 완료된 데이터들을 모아서 하나의 Map으로 조립 후 반환
        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull) // 에러가 발생하여 null이 된 결과물은 제외
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
    }
}