package com.stocklog.global.service;

import com.opencsv.CSVReader; // 단순 split보다 훨씬 안전합니다.
import com.stocklog.domain.stock.common.StockMasterDto;
import com.stocklog.domain.stock.common.type.Currency;
import com.stocklog.domain.stock.common.type.MarketType;
import com.stocklog.domain.stock.common.type.Nation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class StockParserService {

    public List<StockMasterDto> loadMasterStocks() {
        List<StockMasterDto> list = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource("data/stocks_master_final.csv");

        if (!resource.exists()) return list;

        try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            reader.readNext();

            String[] cols;
            while ((cols = reader.readNext()) != null) {
                try {

                    if (cols.length < 7) continue;

                    StockMasterDto dto = StockMasterDto.builder()
                            .shortCode(cols[0].trim())
                            .standardCode(cols[1].trim())
                            .korName(cols[2].trim())
                            .marketType(MarketType.from(cols[3]))
                            .nation(Nation.fromString(cols[4]))
                            .currency(Currency.fromString(cols[5]))
                            .sector(cols[6].trim())
                            .rsym(cols[7].trim())
                            .build();

                    // 필수 값이 null인 경우 로깅 (디버깅용)
                    if (dto.marketType() == null) {
                        log.info("종목 {} : MarketType 파싱 실패 (입력값: {})", cols[2], cols[3]);
                    }

                    list.add(dto);

                } catch (Exception e) {
                    log.error("행 파싱 중 에러 발생: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("마스터 데이터 로드 중 치명적 오류", e);
        }
        return list;
    }
}