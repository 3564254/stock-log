# 아키텍처 · JPA · DB 공부 메모

이 파일은 코드를 바꾸지 않고, 현재 프로젝트를 보며 공부할 만한 내용을 적어 둔 메모입니다.

## FacadeService

```java
// FacadeService는 여러 작업을 묶어 "회원가입", "포트폴리오 분석"처럼
// 한 가지 기능을 완성할 때 사용하면 좋다.
//
// 단, 내부 Service 메서드를 한 줄로 그대로 호출만 한다면
// Facade가 파일 수만 늘리는 중간 전달자가 될 수 있다.
```

- `MemberFacadeService.signUp()`, `sendRequest()`는 여러 작업을 묶으므로 Facade 역할이 있다.
- `PortfolioFacadeService.analyzePortfolio()`도 조회와 분석을 묶으므로 의미가 있다.
- `MarketFacadeService`, `TradeFacadeService`의 단순 전달 메서드는 나중에 줄이거나, 실제 유스케이스를 조합하도록 바꿔 볼 수 있다.

```java
// 추천 기준
// "이 메서드는 여러 작업을 묶어서 사용자 기능 하나를 완성하는가?"
// 예라면 Facade를 유지하고, 아니라면 Service를 직접 호출해도 된다.
```

## 디자인 패턴

```java
// [MemberDomainAdapter와 PurchaseMemberReader를 왜 만들었나?]
//
// purchase(매수/포트폴리오) 기능에서도 회원 정보가 필요하다.
//
// 하지만 purchase 코드가 MemberRepository를 직접 사용하면
// purchase가 member의 내부 저장 방식까지 알아야 한다.
//
// 그래서 purchase 쪽에는 "회원이 필요하다"는 작은 요청서인
// PurchaseMemberReader 인터페이스만 둔다.
//
// 실제로 MemberRepository를 써서 회원을 가져오는 일은
// MemberDomainAdapter가 맡는다.
```

```java
// 흐름
//
// PortfolioService
//     -> PurchaseMemberReader.getMemberProxy(memberId) 호출
//     -> MemberDomainAdapter가 호출을 받음
//     -> MemberRepository.getReferenceById(memberId) 호출
//     -> Member 프록시를 PortfolioService에 돌려줌
//
// PortfolioService는 MemberRepository를 직접 몰라도 된다.
```

```java
// PurchaseStockReader와 StockDomainAdapter도 같은 이유다.
//
// purchase 쪽은 "종목 정보/현재가가 필요하다"고만 말한다.
// StockDomainAdapter가 StockService, StockInfoService,
// StockSummaryQuery를 사용해 필요한 정보를 가져온다.
//
// 즉, 다른 도메인의 Service를 purchase 코드에서 직접 꺼내 쓰지 않게 한다.
```

```java
// 이것은 Port와 Adapter라는 구조를 연습한 것이다.
//
// Port(요청서): PurchaseMemberReader, PurchaseStockReader
// Adapter(실제 처리): MemberDomainAdapter, StockDomainAdapter
//
// 장점:
// 1. purchase가 MemberRepository/StockService에 덜 묶인다.
// 2. 테스트할 때 가짜 Reader를 넣기 쉽다.
// 3. 회원/종목을 가져오는 방법이 바뀌어도 Adapter를 주로 고치면 된다.
```

```java
// 현재 구조에서 더 생각해 볼 점
//
// PurchaseStockReader에 메서드가 너무 많아지면
// purchase가 stock 도메인의 정보를 너무 많이 요구한다는 신호일 수 있다.
//
// 필요한 기능별로 작은 Reader를 나누는 방법도 있다.
// 예: StockSummaryReader, StockInfoReader
//
// 또 PurchaseMemberReader가 Member Entity를 그대로 돌려주므로
// member 도메인과의 연결이 완전히 사라진 것은 아니다.
// 지금은 "Repository/Service를 직접 의존하지 않게 만든 첫 단계"로 이해하면 된다.
```

```java
// [Adapter를 왜 만들었나?]
//
// 외부 API마다 요청 방법과 응답 모양이 다르다.
//
// 예:
// KIS는 KIS 방식의 JSON을 준다.
// Finnhub는 Finnhub 방식의 JSON을 준다.
// FRED는 FRED 방식의 JSON을 준다.
//
// 그런데 내 서비스 코드가 API마다 다른 JSON 형식을 직접 알면
// 서비스 코드가 복잡해지고, API를 바꾸기도 어려워진다.
//
// Adapter는 외부 API의 데이터를
// 우리 서비스가 사용하는 공통 형태로 바꿔 주는 번역기다.
```

```java
// 흐름 예시
//
// StockPriceService
//     -> StockPriceProvider 인터페이스 호출
//     -> KisDomesticPriceProvider 같은 Adapter가 실제 KIS API 호출
//     -> KIS 응답 JSON을 StockPriceDto 같은 우리 형태로 변환
//
// Service는 "어느 API인지"보다 "주가가 필요하다"에만 집중한다.
```

```java
// Adapter를 사용하면 좋은 점
//
// 1. KIS API의 응답 형식이 바뀌어도 Adapter 쪽만 주로 수정한다.
// 2. KIS 대신 다른 증권사 API로 바꿔도 Service 수정이 줄어든다.
// 3. 테스트할 때 실제 API 대신 가짜 Provider를 넣기 쉽다.
// 4. 외부 API의 복잡한 DTO가 domain/service까지 퍼지는 것을 막는다.
//
// 현재 프로젝트에서는 infra 패키지의 KIS, Finnhub, FRED Provider들이
// 이런 Adapter 역할을 하도록 만든 것으로 볼 수 있다.
```

```java
// 주의할 점
//
// Adapter는 "외부 데이터 형식 변환"을 담당한다.
// 매매 판단, 포트폴리오 점수 계산 같은 우리 서비스의 규칙까지 넣으면
// Adapter의 역할이 너무 커진다.
//
// 외부 API 호출/응답 변환: Adapter(infra)
// 우리 서비스의 계산/판단: domain 또는 application service
```

```java
// [Strategy 패턴]
// RSI, 이동평균선, 거래량처럼 매매 규칙이 계속 늘어난다면
// if/switch를 계속 늘리기보다 규칙별 클래스를 따로 만들 수 있다.
//
// 장점: 새 규칙을 추가할 때 기존 코드를 덜 수정한다.
// 관련 위치: TradingRuleService.evaluateSingleRule()
```

```java
// [외부 API 어댑터]
// KIS, Finnhub, FRED 같은 외부 API의 실제 호출 코드는 infra에 둔다.
// Service는 "주가를 가져온다"만 알고,
// 어느 API를 쓸지는 Provider 구현체가 담당하면 API 교체가 쉬워진다.
//
// 현재 Provider 인터페이스 + infra 구현체로 나누려는 방향은 좋다.
```

## JPA 관계와 조회 성능

```java
// TradeReview와 TradingRule은 memberId를 Long으로 직접 저장한다.
//
// 회원과 실제 관계라면 @ManyToOne으로 연결하고 DB FK를 두는 편이 안전하다.
// FK가 있으면 존재하지 않는 회원 ID를 저장하기 어렵다.
//
// 단, 회원 삭제 후에도 기록을 독립적으로 보관하려는 정책이면
// Long ID만 보관하는 방식도 가능하다. 먼저 정책을 정한다.
```

관련 위치: `TradeReview`, `TradingRule`

```java
// 목록을 조회한 뒤 반복문 안에서 연관 데이터를 꺼내면
// DB 조회가 데이터 개수만큼 추가되는 N+1 문제가 생길 수 있다.
//
// 해결 방법: @EntityGraph, fetch join, DTO projection
//
// HoldingRepository의 @EntityGraph 사용은 좋은 방향이다.
// PortfolioService에서 종목별 현재가를 가져오는 부분은
// 여러 종목을 한 번에 조회할 수 있는지 확인해 본다.
```

관련 위치: `HoldingRepository`, `PortfolioService`

```java
// StockChart의 PK는 code + tradeDate + period인 복합키다.
// Repository의 ID 타입도 Long이 아니라 StockChartId여야 한다. (수정 완료)
//
// 또한 code + tradeDate만으로는 period가 여러 개일 수 있으므로
// 조회 결과를 Optional 한 건이라고 가정하면 안 된다. (사용 안하고 있어서 삭제함)
```

관련 위치: `StockChart`, `StockChartRepository`

```java
// StockFinancial, StockValuation은 baseDate가 있지만 PK는 stock_code 하나다.
// 그래서 날짜별 이력을 저장하려면 새 데이터가 기존 데이터를 덮어쓰게 된다.
//
// 최신 데이터 1건만 보관할 목적이면 현재 구조도 가능하다.
// 날짜별 이력이 필요하면 stock_code + base_date를 유일하게 만든다. (수정 완료)
```

관련 위치: `StockFinancial`, `StockValuation`

## DB 제약과 인덱스

```java
// 자주 조회하는 조건에는 인덱스를 둔다.
//
// 회원별 거래 기록 날짜 조회: (member_id, trade_date)
// 회원별 리뷰 작성일 조회:   (member_id, created_at)
// 활성 매매 규칙 조회:        (member_id, is_active)
//
// @ManyToOne 관계를 만든다고 DB 인덱스가 항상 자동 생성되는 것은 아니다.
```

```java
// WatchItem은 회원 + 종목 unique 제약이 있어 중복 저장을 막는 최종 안전장치가 있다.
//
// exists() 후 save() 사이에 동시에 요청이 들어올 수 있으므로,
// DB unique 제약 위반 예외도 "이미 등록된 관심 종목" 예외로 바꾸면 좋다.
```

```java
// Friendship은 A -> B와 B -> A가 별개로 저장될 수 있다.
// 같은 두 사람의 관계가 중복되지 않게 설계가 필요하다.
//
// 방법 예: 작은 회원 ID와 큰 회원 ID를 항상 같은 순서로 저장하고,
// 두 ID 조합에 unique 제약을 둔다.
```

## 트랜잭션과 동시성

```java
// 조회만 하는 메서드: @Transactional(readOnly = true)
// 저장/수정/삭제 메서드: @Transactional
//
// Facade 클래스 전체를 readOnly로 선언한 뒤 쓰기 메서드를 같이 두면 헷갈릴 수 있다.
// public 메서드마다 조회인지 변경인지 명확히 표시하는 편이 이해하기 쉽다.
```

관련 위치: `PortfolioFacadeService`, `TradeFacadeService`

```java
// 회원가입처럼 여러 단계가 하나의 기능이라면 한 트랜잭션으로 묶는다.
//
// 예: 중복 확인 + 비밀번호 암호화 + 회원 저장
// 모두 성공하거나, 하나라도 실패하면 모두 취소되어야 한다.
// 이런 트랜잭션 경계는 Facade/Application Service에 두기 좋다.
```

```java
// Holding을 동시에 수정하면 마지막 저장이 앞의 변경을 덮어쓸 수 있다.
// @Version을 추가하면 "다른 사람이 먼저 수정함"을 감지할 수 있다.
//
// 충돌 시: 다시 조회한 뒤 저장하도록 사용자에게 안내한다.
```

관련 위치: `Holding`

## 금액과 스키마 운영

```java
// 금액, 가격, 환율, 목표가는 Double보다 BigDecimal이 안전하다.
// Double은 계산 결과가 아주 조금 틀어질 수 있다.
//
// 확인할 필드: WatchItem.targetPrice, TradingRule.targetValue,
// TradeLog.quantity/profit, ExchangeRate.rate
```

```java
// ddl-auto: update는 개발 중에는 편하지만 운영용으로는 위험할 수 있다.
// DB 변경 이력은 Flyway 또는 Liquibase로 관리하고,
// 운영에서는 ddl-auto: validate를 사용하는 방식을 공부해 본다.
//
// H2의 MySQL 호환 모드는 실제 MySQL과 완전히 같지 않다.
// 중요한 쿼리와 인덱스는 실제 MySQL에서도 확인한다.
```

관련 위치: `src/main/resources/application.yaml`

## 테스트 설정

```java
// 현재 테스트는 gemini.api.key 설정값이 없어 애플리케이션 시작 단계에서 실패한다.
//
// 테스트에서는 실제 API 키 대신 더미 값이나 Mock Provider를 사용한다.
// 외부 API 호출이 필요한 테스트와 DB/JPA 테스트를 분리하면 테스트가 더 안정적이다.
```
