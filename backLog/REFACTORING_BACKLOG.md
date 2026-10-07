# StockLog 리팩토링 백로그

> 마지막 갱신: 2026-10-06
> 상태 표기: ⬜ 미착수 · 🟨 진행/보류 · ✅ 완료
> 우선순위: P0 = 버그·장애·성능 / P1 = 정합성·안정성 / P2 = 개선·정리

---

## P0

### 1. MarketDiagnosisService — AI 호출을 요청·트랜잭션 밖으로 ⬜
- **위치**: `MarketDiagnosisService.getMarketDiagnosisDto(...)`
- **문제**
  - `@Transactional` 안에서 Gemini를 동기 호출 → AI 응답(10초 이상) 동안 DB 커넥션 점유
  - 매일 첫 방문자의 페이지 로딩이 AI 응답까지 지연됨
  - 오늘 진단이 있어도 코멘트가 에러 문구면 페이지를 열 때마다 AI 재호출
- **방향**
  - 스냅샷(점수/국면)은 짧은 트랜잭션으로 저장하고 `comment = null`로 둔다
  - 코멘트는 별도 빈에서 `@Async` 또는 스케줄러로 생성 (같은 클래스 내부 호출은 `@Async`/`@Transactional`이 적용되지 않으므로 빈 분리 필수)
  - 화면은 `/api/market/ai-comment`로 비동기 로드 (market.html 수정본은 이미 준비됨 → 항목 17)

### 2. 코멘트 에러 처리 방식 ⬜
- **위치**: `MarketDiagnosisService` (`isCommentError`, `log.warn(... substring(0, 10))`)
- **문제**
  - `substring(0, 10)` → 코멘트가 10자 미만(빈 문자열 등)이면 `StringIndexOutOfBoundsException`
  - 에러 문구("AI 분석 중 오류 발생")를 DB에 저장한 뒤 문자열 `equals`로 에러 판별 → `GeminiAiProvider.AI_ERROR_MESSAGE`와 문구가 어긋나면 에러가 그대로 노출
- **방향**: 에러는 예외/`Optional.empty()`로 전달하고 DB에는 성공한 코멘트만 저장. 로그는 `abbreviate`.

### 3. MarketRegimeEngine — confidence 계산 ⬜
- **위치**: `MarketRegimeEngine.evaluate` → `round(abs(weightedScore))`
- **문제**: 가중 평균의 절댓값이라 지표가 엇갈리면 상쇄되어 항상 한 자리 수. 화면 툴팁은 "지표 간 의견 일치도"를 의미한다.
- **방향**: `confidence = 100 × (0.6 × 합의도 + 0.4 × 세기)`, 괴리 경고 시 ×0.85. 임계값(NEUTRAL_BAND=10, 세기 기준 40)은 임의의 시작값이므로 최근 데이터로 분포 확인(20~80 목표) 후 조정.

### 4. PortfolioApiController — 주봉 차트 조회 버그 / 매도 검증 미구현 ⬜
- **위치**: `verifyBuy`의 `TODO(버그)`, `verifySell` TODO 스텁
- **문제**: 주봉 컨텍스트를 만들 때 일봉 조회 메서드를 호출. 매도 검증은 구현되지 않음.
- **방향**: 주봉 조회 메서드(StockChartService)를 확인해 교체. `verifySell`은 `TradingSignalAnalyzer` 결과 기반으로 구현.

---

## P1

### 5. 시장 종합 점수(composite) 단일화 ⬜
- `MarketDiagnosisService.compositeScore`는 서브 그룹 4개의 단순 평균인데 **risk(가중치 15%)와 credit(10%)이 어느 그룹에도 없음**
- `divergence`(경고 점수)가 방향 지표처럼 평균에 포함됨
- 엔진의 `weightedScore`와 계산이 달라 화면의 종합 점수와 국면 판정이 불일치
- **방향**: `weightedScore`를 한 곳에서 계산해 `MarketRegimeDto`로 전달, 서브 점수는 표시용으로만 사용. `MarketDiagnosisHistory`에 `riskScore`도 저장.

### 6. 날짜·동시성·휴장일 ⬜
- `LocalDate.now()`가 서버 타임존에 의존 → `Clock` 주입 + `ZoneId` 명시
- `findByDate` 후 `save` 구조 → 동시 요청 시 중복 생성. `date` unique 제약 + `DataIntegrityViolationException` 처리, 또는 하루 한 번 스케줄러 생성
- 주말·휴장일에도 새 행과 streak이 쌓임
- 5일 변화량이 `PageRequest.of(4, 1)`(행 개수 기준). 기록 5개 미만이면 0.0이 "변화 없음"으로 보임 → 날짜 기준 조회, 없으면 null

### 7. 시장 엔진 안정성 ⬜
- `c.creditRegime().equals("STRESS")` → `"STRESS".equals(...)` (null NPE)
- `collectWarnings`는 `macro().score() < 0`(-1점도 경고) → 임계값 도입(-20 등)
- 점수에 NaN이 섞이면 `Math.round`가 0을 반환해 데이터 누락이 조용히 숨겨짐
- `getWeightedScore`/`collectWarnings`는 null 체크 없음 (`extract*`와 불일치)
- 단기 조정 오버라이드가 BULL에만 있고 BEAR(단기 반등) 쪽은 없음, 덮어쓰면 RISK 정보 소실
- divergence 가중치 0.35 때문에 전체 가중치가 최대 1.35 → 별도 감점 단계로 분리

### 8. 문자열 → enum, 화면 계약 불일치 확인 ⬜
- `"BULL"`, `"RISK_ON"`, `"BULL + RISK_ON"` 문자열 조합 switch → `Trend`, `Risk` enum
- 엔진은 `RISK_ON`(언더스코어), market.html은 `'RISK-ON'`(하이픈) 비교 → DTO 변환 여부 확인 (미변환이면 위험 배지가 항상 회색)
- `divergences.add("None")` 센티널 → 빈 리스트 + 화면에서 `isEmpty` 판단

### 9. JSON 재파싱 제거 ⬜
- `rawJson` 생성 후 필드 3개를 뽑으려고 `readTree`를 3번 호출, 키 문자열이 record 컴포넌트명과 어긋나면 조용히 null
- **방향**: `rawDetails()`를 `instanceof MacroDetails d` 패턴으로 타입 있게 읽기 (엔진과 동일 방식)

### 10. TradingSignalAnalyzer 잔여 점검 ⬜
- `Inputs.from`의 `ctx.momentum().atr()`, `ctx.volume().vwap20()` — 둘 다 nullable인데 null 체크 없음
- `lowerWickRatio` / `upperWickRatio` 미사용
- `triggerConfirmed` vs `bullishTrigger`: open이 NaN일 때 판정 불일치
- RR이 NaN이면 손익비 검사가 건너뛰어짐
- `WARNING_RSI` import 출처 확인

### 11. 타점 모델 중복 정리 (제안만, 미착수) ⬜
- `EntryPlan` / `PriceTarget` / `RiskManagement`에 겹치는 필드 → 하나의 `Plan`으로 통합
- `ChartAnalysis` 안의 `MarketContext`는 별도 모델 속성(`ctx`)으로 분리
- 영향 범위: `TradingSignalResult`, `TradingSignalAnalyzer`, `GeminiAiProvider`, `analysis.html`

### 12. 마이그레이션 가정 확인 ⬜
- `tradeService.updateAnalyzedSignal(memberId, stockCode, chartPhase, double)` 시그니처는 추정
- `primaryEntry` 의미가 바뀜(legacy: 지지선 기반 → 현재: refPrice)
- `TradingSignalAnalyzer`의 패키지(`stock.analysis.diagnosis`)와 클래스명 가정
- 삭제 대상 파일 직접 삭제 확인: `SignalRule`, `MomentumRule`, `RsiSignalRule`, `TrendAndMaRule`

---

## P2

### 13. analysis.html 개선 ⬜
- 차트에 진입 구간 / 손절 / 목표 가격선 (`createPriceLine`, 값은 `priceTarget`에 이미 있음)
- 제목에 종목명 표시 (현재는 코드만)
- `isKr` 정규식 불일치 (Thymeleaf `^[0-9]+$` vs JS `/^\d{6}$/`)
- `backUrl`을 템플릿에서 읽지만 컨트롤러가 넣지 않음
- `zoneStrength` 노출 (타입 확인 후)
- 구간 위/안/아래 판정을 템플릿이 아니라 서버 `Verdict`에서 내려받기 (경계값 불일치 방지)
- ✅ 완료: verdict 배지, 현재가 대비 진입 구간 표시, `${analysis.signals}` → `${signals}`

### 14. 에러 페이지 ⬜
- 500 페이지가 `errorMessage`를 그대로 표시 → 예외 메시지(SQL, 클래스명)가 노출되지 않도록 컨트롤러에서 안전한 문구만 전달
- 에러 페이지에는 `loginMember`가 없을 수 있어 header fragment 미사용 (의도된 동작)
- ✅ 완료: viewport 메타 추가, 카드 레이아웃, 홈 링크 `/static` → `/`, `/templates` 접두사 제거 (실제 컨트롤러 매핑 일치 여부는 미확인)

### 15. 포트폴리오 화면 가정 확인 ⬜
- `StockQuote`가 `item.stock.code/name`을 갖는다는 가정
- `AddPortfolioStockRequest`가 `{code, name, averagePrice}`를 받는다는 가정
- 서버가 `detail.actualValue`를 내려주면 규칙 검증 화면에 표시됨 (선택)

### 16. 문서·정리 ⬜
- `PROJECT_CONTEXT.md` 갱신: `EntrySignalAnalyzer`(삭제됨), `SignalRule` 계열(삭제됨), `StockAnalysisResult.signals` 제거, `TradingSignalResult` 구조, 시장 confidence 설명
- `market_improved.html`, `analysisLegacy.html`, 개선본 fragment 중복 → 참조 검색 후 정리
- 테스트 부족: `TradingSignalAnalyzer`, `MarketRegimeEngine`에 경계값 단위 테스트

### 17. market.html AI 코멘트 비동기 로딩 🟨 (보류)
- market.html 수정본 작성 완료 (스켈레톤 + "분석 중" 안내 + 실패 시 재시도, 서버가 코멘트를 채워 주면 그대로 사용)
- 남은 작업: 컨트롤러에서 코멘트를 모델에서 제거하고 `GET /api/market/ai-comment` 추가, 서버 캐시 (항목 1과 함께 진행)

---

## 참고: 공통 원칙
- 화면 계약(모델 attribute 이름)을 바꿀 때는 컨트롤러와 템플릿을 같이 수정한다.
- 점수 계산을 바꿀 때는 기존 공식·결측 처리·경계값과 화면 영향을 같이 확인한다.
- 이 문서의 "추정", "가정"으로 표시한 항목은 실제 코드로 확인한 뒤 진행한다.
