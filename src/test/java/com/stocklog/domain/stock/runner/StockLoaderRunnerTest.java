package com.stocklog.domain.stock.runner;

import com.stocklog.domain.stock.stock.StockService;
import com.stocklog.global.init.StockLoaderRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class StockLoaderRunnerTest {

    @Autowired
    private StockService stockService;

    @Autowired
    private StockLoaderRunner stockLoaderRunner;

    @Test
    @DisplayName("DB가 비어있을 때 Runner를 실행하면 데이터가 채워져야 함")
    void runTest() throws Exception {

        // stockLoaderRunner.run(); 자동실행

        // 결과 검증
        long count = stockService.getCount();
        System.out.println("저장된 종목 수: " + count);
        assertThat(count).isGreaterThan(0);
    }

}