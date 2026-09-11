# 어댑터와 유스케이스 구분하기

헥사고날 아키텍처에서 어댑터와 유스케이스가 섞여 보일 때는 다음처럼 구분한다.

- **유스케이스(애플리케이션 계층)**: 시스템이 무엇을 할지 결정하고 업무 규칙 및 처리 순서를 조합한다. 예: 재고를 기록한다, 재고 이력을 조회한다.
- **포트(Port)**: 유스케이스가 외부와 통신하기 위해 요구하는 인터페이스다.
- **어댑터(Adapter)**: 포트를 HTTP, JPA, Kafka, 외부 API 같은 실제 기술로 구현한다.

## 일반적인 흐름

```text
REST Controller (인바운드 어댑터)
        ↓
UseCase 인터페이스 (인바운드 포트)
        ↓
UseCase 구현체 / 서비스 (애플리케이션)
        ↓
RepositoryPort (아웃바운드 포트)
        ↓
JpaRepositoryAdapter (아웃바운드 어댑터)
        ↓
DB
```

## 판단 기준

- Spring MVC, JPA, Kafka, 외부 API처럼 **기술을 아는 코드**는 어댑터다.
- 업무 규칙과 처리 순서를 결정하는 코드는 유스케이스다.
- 유스케이스가 `JpaRepository`, HTTP DTO, `@Entity`를 직접 안다면 계층이 섞였는지 점검한다.

## 예시 구조

```java
// application/port/in
public interface RecordStockUseCase {
    void record(RecordStockCommand command);
}

// application/service
@RequiredArgsConstructor
public class RecordStockService implements RecordStockUseCase {
    private final LoadProductPort loadProductPort;
    private final SaveStockLogPort saveStockLogPort;

    @Override
    public void record(RecordStockCommand command) {
        // 업무 규칙과 처리 흐름
    }
}

// adapter/in/web
@RestController
@RequiredArgsConstructor
class StockController {
    private final RecordStockUseCase recordStockUseCase;
}

// adapter/out/persistence
@Component
class StockLogPersistenceAdapter implements SaveStockLogPort {
    // JPA 변환·저장 구현
}
```
