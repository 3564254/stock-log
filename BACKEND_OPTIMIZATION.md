# StockLog 백엔드·DB 최적화 백로그

> 작성: 2026-10-08
> 상태 표기: ⬜ 미착수 · 🟨 진행/보류 · ✅ 완료
> 우선순위: P0 = 체감 성능 / P1 = DB·정합성 / P2 = 구조·운영
> 기존 `REFACTORING_BACKLOG.md`(시장 진단·시그널 엔진 중심)와 겹치지 않는 항목 위주.

---

## P0. 체감 성능

### 1. 포트폴리오 시세 조회가 종목 수만큼 순차 외부 호출 ⬜
- **위치**: `PortfolioService.getMyPortfolio`
- **문제**
  - 보유 종목마다 `summaryUseCase.getStockSummary(code)` 호출 → 내부에서 `fetchInfoDto` + `fetchPrice`를 둘 다 실행. 필요한 건 현재가뿐.
  - `StockPriceService.fetchPricesInBulk`가 이미 있는데 사용하지 않음 (코드의 TODO도 같은 지적).
  - 홈·포트폴리오를 열 때마다 반복.
- **방향**: `holding.getStock().getCode()` 목록으로 `fetchPricesInBulk` 한 번 호출 → Map에서 현재가 조회. 가격이 없는 종목은 `null`로 두고 화면은 "-" 표시.

### 2. fetchPricesInBulk 보강 ⬜
- **위치**: `StockPriceService.fetchPricesInBulk`, `fetchPrice`
- **문제**
  - `supplyAsync`에 Executor 미지정 → 공용 풀(`commonPool`) 사용, 다른 병렬 작업과 간섭
  - `join()`에 타임아웃 없음 → 외부 API 하나가 멈추면 페이지 전체 지연
  - `fetchPrice` 안에서 종목마다 `stockService.getStockDto(code)` DB 조회
- **방향**: 전용 `ThreadPoolTaskExecutor`, 호출별 타임아웃(2~3초, `orTimeout`/`completeOnTimeout`), 시장 구분은 캐시(항목 3)로 해결.

### 3. 캐시 전무 ⬜
- 프로젝트 전체에 `@Cacheable`, `@Async`, `@Scheduled`가 하나도 없음.
- 같은 종목 시세를 사용자 수 × 새로고침 횟수만큼 외부 API로 재조회 → 공급자 호출 한도 위험.
- **방향**: Caffeine 도입.

| 대상 | 제안 TTL | 비고 |
|---|---|---|
| 시세(`fetchPrice`) | 30~60초 | 키: 종목코드 |
| 종목 정보/시장 구분(`getStockDto`, `fetchInfoDto`) | 수 시간 | 거의 불변 |
| 차트(일봉) | 장 마감 후 길게 | 항목 4·5와 함께 |

### 4. 차트 저장(`fetchAndSave`)의 SELECT 폭증과 중복 ⬜
- **위치**: `StockChartService.fetchAndSave`
- **문제**
  - `StockChart`는 복합키(`code`, `tradeDate`, `period`) 직접 할당 엔티티 → `saveAll`이 `merge`로 동작하며 행마다 SELECT를 먼저 실행할 수 있음
  - "삭제 후 삽입" 로직이 주석 처리된 상태
- **방향**: 해당 종목·기간 행을 한 번에 삭제 후 일괄 삽입, 또는 새 행만 골라 삽입(upsert). `hibernate.jdbc.batch_size`, `order_inserts` 활성화.

### 5. isMarketOpen 디버그 코드 ⬜
- **위치**: `StockChartService.isMarketOpen`
- 비KR이면 무조건 `true` → 미국 종목은 캐시를 한 번도 쓰지 않고 매번 외부 API 호출.
- `LocalTime.now()`가 서버 로컬 시간 의존.
- **방향**: `Clock` 주입 + `ZoneId`(KR: Asia/Seoul, US: America/New_York) 명시, 휴장일/주말 처리. (기존 백로그 6번과 같은 계열)

---

## P1. DB·정합성

### 6. 인덱스 점검 ⬜

| 테이블 | 조회 패턴 | 현재 | 제안 |
|---|---|---|---|
| `stock_chart` | `code + period` 필터, `tradeDate` 정렬 | `(stock_code, trade_date)` — `period` 없음, PK 앞부분과 중복 | `(stock_code, period, trade_date)` 또는 PK 순서 조정 |
| `trade_log` | `member + 날짜 범위` | 없음 | `(member_id, trade_date)` |
| `trade_review_memo` | `memberId + createdAt 범위` | 없음 | `(member_id, created_at)` |
| `trading_rule` | `memberId + isActive` | 없음 | `(member_id, is_active)` |
| `holding`, `watch_item` | `member + stock` | 유니크 제약이 인덱스 역할 | 변경 없음 |

### 7. 차트 읽기 전량 조회 ⬜
- `fetchChartAsc`가 호출마다 해당 종목의 전체 일봉을 로드. 지표 계산에 필요한 최근 N개(예: 250일)만 읽도록 제한.
- 매 호출 앞의 `stockService.getStockDto(code)` DB 조회도 캐시(항목 3)로 제거.

### 8. 동시 요청 중복 처리 ⬜
- 같은 종목 차트를 동시에 두 사용자가 열면 외부 호출·저장이 중복.
- **방향**: 종목 단위 락(또는 in-flight 요청 공유), 장기적으로 하루 한 번 스케줄러 갱신.
- 기존 백로그 6번(`MarketDiagnosis` 중복 생성)과 같은 종류.

---

## P2. 구조·운영

### 9. 매수 검증이 동기식 ⬜
- AI 리포트까지 한 요청에서 대기 (화면 타임아웃 60초가 이 때문).
- **방향**: 분석 결과를 먼저 응답하고 AI 문구는 별도 조회, 또는 같은 종목의 최근 분석을 몇 분 캐시. (기존 백로그 1번과 같은 계열)

### 10. 사용자별 요청 제한 ⬜
- 화면은 429를 처리하지만 서버에 속도 제한이 있는지 확인 필요. 검증 API는 외부 AI 비용이 들므로 사용자별 제한(예: 분당 N회) 권장.

### 11. 일관성 정리 ⬜
- `TradeReviewService`는 `IllegalArgumentException`, 다른 서비스는 `BusinessException` → 통일
- 상위 서비스(`PortfolioDashboardService`)에 `@Transactional(readOnly = true)` 없음 → 한 요청에서 트랜잭션이 여러 번 열림
- 소유권 검증(`validateRuleOwnership`, `validateReviewOwnership`)은 이미 적용되어 있음 ✅

### 12. 설정 확인 (이번에 코드로 확인하지 못함) ⬜
- `spring.jpa.open-in-view` (끄기 권장)
- `hikari.maximum-pool-size`
- `hibernate.jdbc.batch_size`, `order_inserts`
- 외부 API 호출 연결/읽기 타임아웃
- 슬로우 쿼리 로그 및 요청당 쿼리 수 측정 (Hibernate statistics)

---

## 권장 진행 순서

1. 포트폴리오 시세 일괄 조회 + 타임아웃 (항목 1·2)
2. Caffeine 캐시 도입 (3)
3. 차트 저장 방식·`isMarketOpen` 수정 (4·5)
4. 인덱스 추가 (6)
5. AI 호출 분리(9)와 기존 백로그 P0

## 측정 방법 (전후 비교)
- 홈·포트폴리오 첫 응답 시간 (보유 종목 5/10개)
- 요청당 SQL 수와 외부 API 호출 수
- 같은 종목 연속 조회 시 외부 호출이 줄었는지 (캐시 적중)
