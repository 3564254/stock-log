# 개선할 것

## 성능

- [ ] 외부 시세 조회에 전용 스레드 풀과 타임아웃 적용 (`fetchPricesInBulk`)
- [ ] 자주 바뀌지 않는 값(종목 정보 · 시장 구분)에 캐시 도입
- [ ] 차트 저장을 일괄 삽입으로 바꾸고 JPA batch 설정 켜기
- [ ] 차트 조회 시 전체 일봉 대신 필요한 최근 N개만 읽기

## DB

- [ ] 인덱스 점검: `stock_chart (code, period, trade_date)`, `trade_log (member_id, trade_date)`
- [ ] `spring.jpa.open-in-view` 끄기
- [ ] 같은 종목을 동시에 요청할 때 외부 호출이 겹치지 않게 하기

## 구조 · 운영

- [ ] 매수 검증 응답에서 AI 문구를 분리해 먼저 응답하기
- [ ] 검증 API에 사용자별 요청 제한
- [ ] 예외 타입 통일 (`IllegalArgumentException` → `BusinessException`)
- [ ] Spring Security 도입, Flyway로 스키마 이력 관리
