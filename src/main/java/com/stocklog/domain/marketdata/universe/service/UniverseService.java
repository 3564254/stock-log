package com.stocklog.domain.marketdata.universe.service;

import com.stocklog.domain.marketdata.universe.dto.UniverseDto;
import com.stocklog.domain.marketdata.universe.repository.UniverseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UniverseService {

    private final UniverseRepository universeRepository;

    /**
     * 캐시 히트 검증: 유효하다면 엔티티가 아닌 'DTO'로 변환하여 반환
     */
    @Transactional(readOnly = true)
    public Optional<UniverseDto> getFreshStatus(String code) {
        return universeRepository.findById(code)
                .filter(status -> status.getLastModifiedDate() != null &&
                        status.getLastModifiedDate().toLocalDate().isEqual(LocalDate.now()))
                .map(UniverseDto::from); // 엔티티 -> DTO 변환 후 반환
    }

    /**
     * DTO를 받아 엔티티로 변환 후 저장 (기존 로직 유지)
     */
    @Transactional
    public void saveOrUpdate(UniverseDto dto) {
        universeRepository.findById(dto.code()).ifPresentOrElse( // record의 getter는 code()
                status -> {
                    // status 엔티티 내부에 dto 값을 매핑하는 update 메서드가 있다고 가정
                    status.updateStatus(dto.phase(), dto.currentPrice(), dto.changeRate(), dto.entryPrice());
                    universeRepository.save(status);
                },
                () -> universeRepository.save(UniverseDto.toEntity(dto))
        );
    }


}