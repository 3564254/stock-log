package com.stocklog.modules.stock.price;

import com.stocklog.modules.stock.application.request.StockRequest;
import com.stocklog.modules.stock.price.dto.StockPriceDto;
import com.stocklog.modules.stock.price.dto.StockPriceResponse;
import com.stocklog.modules.stock.stock.StockService;
import com.stocklog.modules.stock.stock.dto.StockDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

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

    private static final long RETRY_DELAY_MS = 300;

    public StockPriceDto fetchPrice(String code) {
        StockDto stock = stockService.getStockDto(code);
        StockPriceProvider provider = providers.stream()
                .filter(p -> p.supports(stock.market()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 시장이거나 존재하지 않는 종목입니다: " + code));
        return StockPriceDto.from(fetchWithRetry(provider, StockRequest.from(stock), code));
    }

    // KIS는 초당 호출 한도를 넘거나 순간적으로 불안정하면 500/429를 돌려준다.
    // 종목 여러 개를 연달아 부를 때 자주 생기므로, 이런 일시 오류만 잠깐 쉬었다가 한 번 더 시도한다.
    private StockPriceResponse fetchWithRetry(StockPriceProvider provider, StockRequest request, String code) {
        try {
            return provider.fetchPrice(request);
        } catch (WebClientResponseException e) {
            boolean transientError = e.getStatusCode().is5xxServerError() || e.getStatusCode().value() == 429;
            if (!transientError) throw e;
            log.info("[StockPriceService] {} 일시 오류({}), {}ms 뒤 한 번 더 시도", code, e.getStatusCode().value(), RETRY_DELAY_MS);
            try {
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw e;
            }
            return provider.fetchPrice(request);
        }
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