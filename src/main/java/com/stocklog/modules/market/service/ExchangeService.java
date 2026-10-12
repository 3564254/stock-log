package com.stocklog.modules.market.service;

import com.stocklog.modules.market.exchange.dto.ExchangeRateInfo;
import com.stocklog.modules.market.exchange.entity.ExchangeRate;
import com.stocklog.modules.market.exchange.provider.ExchangeRateProvider;
import com.stocklog.modules.market.exchange.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ClassUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final List<ExchangeRateProvider> providers;   // @Order 순서 (Toss → 기존 API)
    private final ExchangeRateRepository exchangeRateRepository;

    // TODO: 외부 API 호출이 트랜잭션 안에 있다. 호출 빈도가 낮아(최대 10분에 1번) 당장은 두지만,
    //       나중에 조회(트랜잭션 밖)와 저장(짧은 트랜잭션)을 분리한다.
    @Transactional
    public ExchangeRateInfo fetchExchangeRate() {
        LocalDateTime now = LocalDateTime.now(KST);
        Optional<ExchangeRate> saved = exchangeRateRepository.findMainRate();

        // 1) 아직 유효하면 DB 값 사용
        if (saved.isPresent() && saved.get().isFresh(now)) {
            return ExchangeRateInfo.from(saved.get());
        }

        // 2) 없거나 만료됐으면 공급자 순서대로 조회해서 같은 행에 덮어쓴다
        ExchangeRateInfo fresh = fetchFromProviders();
        if (fresh != null) {
            saved.ifPresentOrElse(
                    e -> e.update(fresh, now),
                    () -> exchangeRateRepository.save(ExchangeRate.from(fresh, now)));
            log.info("[Exchange] 환율 갱신: {}", fresh.baseRate());
            return fresh;
        }

        // 3) 모두 실패: 고정값보다는 지난 실제 환율이 낫다
        return saved.filter(ExchangeRate::hasValidRate)
                .map(e -> {
                    log.warn("[Exchange] 공급자 전부 실패 → 지난 환율 사용 ({} 기준)", e.getFetchedAt());
                    return ExchangeRateInfo.from(e);
                })
                .orElseGet(() -> {
                    log.warn("[Exchange] 공급자 전부 실패, 저장된 환율도 없음 → 고정값 사용 (저장하지 않음)");
                    return ExchangeRateInfo.createFallback();
                });
    }

    private ExchangeRateInfo fetchFromProviders() {
        for (ExchangeRateProvider provider : providers) {
            String name = ClassUtils.getUserClass(provider).getSimpleName();
            try {
                ExchangeRateInfo info = provider.fetchExchangeRate();
                if (info != null && info.hasValidRate()) return info;
                log.warn("[Exchange] {} 응답에 유효한 환율이 없어 다음 공급자로 넘어갑니다", name);
            } catch (Exception e) {
                log.warn("[Exchange] {} 실패, 다음 공급자로 넘어갑니다: {}", name, e.getMessage());
            }
        }
        return null;
    }
}
