package com.stocklog.global.init;

import com.stocklog.global.common.StockCache;
import com.stocklog.global.service.StockParserService;
import com.stocklog.domain.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
@Slf4j
public class StockLoaderRunner implements CommandLineRunner {

    private final StockParserService stockParserService;
    private final StockService stockService;
    private final StockCache stockCache;


    @Override
    public void run(String... args) throws Exception {
        long count = stockService.getCount();

        if (count == 0) {
            log.info("데이터가 없습니다. 파일 파싱 및 저장을 시작합니다...");
            stockService.saveAll(stockParserService.loadMasterStocks());
        } else {
            log.info("이미 데이터가 존재합니다. 파싱을 건너뜁니다.");
        }

        stockCache.loadStockNamesToCache();
        log.info("메모리 캐시 로드 완료.");
    }



}
