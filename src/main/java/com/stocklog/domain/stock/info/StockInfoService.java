package com.stocklog.domain.stock.info;

import com.stocklog.domain.stock.common.StockMasterDto;
import com.stocklog.domain.stock.info.dto.internal.StockInfoDto;
import com.stocklog.domain.stock.common.StockRequest;
import com.stocklog.domain.stock.info.dto.response.StockInfoResponse;
import com.stocklog.domain.stock.stock.StockService;
import com.stocklog.domain.stock.stock.dto.StockDto;
import com.stocklog.global.common.StockCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockInfoService {

    private final StockInfoRepository stockInfoRepository;
    private final List<StockInfoProvider> providers;
    private final StockService stockService;


    @Transactional // 저장 가능하도록 설정
    public StockInfoDto fetchInfo(String code) {
        return stockInfoRepository.findById(code)
                .map(StockInfoDto::from)
                .orElseGet(() -> fetchAndSaveFromExternal(code));
    }

    @Transactional // 외부 API 호출 및 DB 저장이 발생할 수 있으므로 트랜잭션 추가
    public List<StockInfoDto> fetchinfoByKeyword(String keyword, Pageable pageable) {

        Slice<StockInfo> stockInfos = stockInfoRepository.findByKeyword(keyword, pageable);

        // 기존 2중 루프를 제거하고, '저장 안 되어 있으면 저장'하는 fetchInfo 메서드를 재사용
        return stockInfos.getContent().stream()
                .map(info -> fetchInfo(info.getCode())) // 엔티티 식별자 메서드명(getCode, getId 등)에 맞춰 사용
                .collect(Collectors.toList());
    }

    public long getCount() {
        return stockInfoRepository.count();
    }

    private StockInfoDto fetchAndSaveFromExternal(String code) {
        StockDto stockDto = stockService.getStock(code);

        StockInfoResponse response = providers.stream()
                .filter(p -> p.supports(stockDto.market()))
                .findFirst()
                .map(p -> p.fetchInfo(StockRequest.from(stockDto)))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 종목입니다: " + code));

        StockInfoDto dto = StockInfoDto.of(stockDto, response);

        // 엔티티 변환 로직을 엔티티 내부 메서드에 맡기면 서비스가 깔끔해짐
        stockInfoRepository.save(StockInfoDto.toEntity(dto));
        return dto;
    }





}
