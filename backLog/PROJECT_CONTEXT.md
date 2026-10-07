# Project Context

> 이 문서는 저장소의 소스 코드와 설정을 기준으로 작성했다. 코드에서 실행 연결을 확인할 수 없는 항목은 예정/부분 구현으로 분리한다. `README.md` 및 일부 주석/문서는 현재 터미널에서 문자 인코딩이 깨져 읽힐 수 있어 구현 근거로 단독 사용하지 않는다.

## 1. Project Overview

StockLog는 국내·해외 종목을 조회·분석하고 개인 보유 종목, 거래 기록, 관심 종목, 매매 규칙을 관리하는 Spring 웹 애플리케이션이다. 소스에는 시장 지수·환율·뉴스 대시보드, 종목 차트·재무·밸류에이션 분석, 사용자/친구 및 포트폴리오 기능이 구현되어 있다. AI Provider 인터페이스와 Gemini 연동 클래스가 있으나 모든 AI 화면/경로의 완전한 연결 여부는 기능별로 다르다.

주요 사용자 흐름은 다음과 같다.

- 시장 화면: 브라우저 → `MarketController` → `MarketFacadeService` → 지수/뉴스/시장 분석 서비스 → 화면 모델 → `templates/market/market.html`.
- 종목 분석: 브라우저 → `StockController` → `StockSnapshotUseCase`로 시세·정보·차트·재무·밸류에이션을 모음 → 종목 분석 및 거래 신호 분석 → `stock/analysis` 템플릿.
- 홈: `MainController` → `MainDashboardService` → 대시보드 집계 → `main/main.html`.
- 종목/시장 원천 데이터는 KIS, FRED, Finnhub 등 Provider Adapter가 가져오며, 일부 시장 데이터는 JPA 저장 데이터를 통해 제공된다.

## 2. Technology Stack

빌드 근거는 `build.gradle`이다.

- Java 17, Gradle Wrapper, Spring Boot Gradle Plugin 4.0.2, dependency-management 1.1.7.
- Spring MVC (`spring-boot-starter-webmvc`), Spring Data JPA, Thymeleaf 및 Thymeleaf Layout Dialect, Validation, Actuator, H2 Console, WebFlux `WebClient` 사용.
- DB 드라이버: H2 및 MySQL Connector/J. 기본 `application.yaml`은 MySQL, `application-test.yml`은 H2 메모리 DB.
- Lombok, Jackson Java Time, OpenCSV 5.9, YahooFinanceAPI 3.17.0, Rome 2.1.0, Spring Security Crypto, AOP, P6Spy 1.9.2, 로컬 JAR `libs/yfinance4j-0.1.7.jar`가 선언되어 있다.
- 테스트는 JUnit Platform 및 Spring Boot 테스트 스타터 기반이다.
- 화면은 Thymeleaf + JavaScript, Alpine.js 사용 흔적, Bootstrap 5.3.3 및 Bootstrap Icons CDN 사용. 시장 차트에는 Lightweight Charts 4.2.0, 친구 포트폴리오 차트와 공통 레이아웃에는 Chart.js 사용 흔적이 있다.
- 별도 프론트엔드 빌드 시스템, MyBatis, Flyway/Liquibase 선언은 확인되지 않았다.

## 3. Project Architecture

큰 틀은 `web`(MVC 진입점) → `modules`(업무 기능과 도메인) → `infra`(외부 시스템 Adapter)이며, 공통 설정/예외/AOP는 `global`에 둔다. 다만 모든 기능이 동일한 계층 분리를 따르는 것은 아니다. `application` use case와 `modules`의 기존 Service가 함께 존재하고, 코드 주석에도 책임 이동 후보가 남아 있다.

- `web.controller`: Thymeleaf 페이지 라우팅과 화면 Model 구성.
- `web.api`: JSON/폼 비동기 API 라우팅.
- `modules.*`: 종목, 시장, 회원, 매매/포트폴리오의 업무 모델·서비스·저장소·분석.
- `infra.*`: KIS/FRED/Finnhub/Gemini 등 외부 통신과 응답 DTO.
- `global.*`: 설정, 인증 사용자 인자 해석, 인터셉터, 예외, 캐시, 초기 데이터 로딩, AOP.
- `application.dashboard`: 홈 대시보드 조합 경계. 현재 `MainDashboardService` 및 `MainDashboardUseCase`에 책임 이전 TODO가 있다.

## 4. Package Structure

`src/main/java/com/stocklog` 아래 주요 패키지:

- `application.dashboard`: `MainDashboardResponse`, `MainDashboardService`, `MainDashboardUseCase`.
- `global`: `annotation`, `aop`, `common`, `config`, `error`, `init`, `service`.
- `infra`: `alpha`, `exchange`, `finnhub`, `fred`, `gemini`, `kis`, `toss` 외부 연동 Adapter/config/DTO.
- `modules.ai`: `AiProvider`.
- `modules.common.finance`: 차트 DTO, 시장 컨텍스트, 지표 계산기, 수학/금융 유틸리티 및 공통 타입.
- `modules.market`: analysis, application, chart, diagnosis, domain, exchange, index, news, service.
- `modules.member`: 회원/친구 관계, DTO, 저장소 및 서비스.
- `modules.purchase`: portfolio 및 trade 기록/규칙/관심종목.
- `modules.stock`: analysis, application, chart, diagnosis, financial, info, price, stock, valutation.
- `web`: API Controller와 페이지 Controller.

`valutation` 철자는 실제 패키지명이다. 새 코드에서 임의로 `valuation`으로 바꾸면 기존 참조와 호환되지 않는다.

## 5. Domain Model

대표 영속 객체와 역할(전체 필드 목록이 아닌 코드상 핵심 필드/관계):

- `modules.stock.stock.Stock` (`stock` 테이블): 종목 식별/시장/자산 기본 모델. `StockRepository`, `StockService`가 관리하며 종목 차트·정보 등의 종목 코드 기준이 된다.
- `StockInfo` (`stock_info`): 종목명, 국가/시장, 업종, 자산 유형 등 상세 종목 정보. 분석용 `StockInfoDto`로 전달된다.
- `StockPrice` (`stock_price`): 현재 가격 및 시가총액 등 시세 데이터.
- `StockChart` (`stock_chart`): 종목별 날짜 기반 차트/캔들 데이터. `StockChartId` 등의 복합 키 및 Provider/Repository/Service 계층을 둔다.
- `StockFinancial` (`stock_financial`): ROE, 영업이익률, 매출/EPS 성장, 부채비율, 유동비율, 배당수익률 등 재무 지표.
- `StockValuation` (`stock_valuation`): PER/PBR/PEG 등 밸류에이션 입력 데이터. 패키지명은 `valutation`.
- `MarketIndex`, `MarketIndexHistory`, `ExchangeRate`, `News`: 시장 지수 현재/이력, 환율, 뉴스 영속 데이터.
- `MarketDiagnosis`, `MarketDiagnosisHistory`: 시장 진단 기록/이력 모델. 저장소와 서비스가 있으나 최신 분석 경로에서의 영속 여부는 해당 메서드별로 확인해야 한다.
- `Member`, `Friendship`: 회원과 친구 관계.
- `Holding`, `PortfolioSnapshot`: 보유 종목과 포트폴리오 스냅샷. `Holding`은 `Stock`/회원과 연계되고 보유수량·평균 매입가를 다룬다.
- `TradeLog`, `TradeReview`, `TradingRule`, `WatchItem`: 거래 기록, 복기 메모, 매매 규칙, 관심 종목.

분석 경계 객체는 JPA Entity와 별개다. `StockSnapshot`은 `StockSnapshotUseCase`의 종목 데이터 묶음이며 `StockAnalysisResult`, `MomentumScore`, `TechnicalScore`, `ValuationScore`, `EntrySignal`, `TradingSignalDto` 등은 계산 결과/DTO다. 시장 분석은 `MarketChartData`, `EngineInput`, `MarketAnalyzerScore`, `MarketRegimeDto/Response`를 사용한다.

## 6. Core Analysis System

### 종목 분석

`StockController.analysisPage`는 로그인 사용자를 요구하고 `code`를 받는다. `StockSnapshotUseCase.getStockSnapshot(code)`로 원천 정보를 모은 다음 `StockAnalysisService.getAnalysisResult(snapshot)`을 호출한다. 이 서비스는 `StockFacadeAnalyzer`에 분석 책임을 위임한다. 추가로 `TradingSignalAnalyzer`는 일/주봉 `MarketContext`를 받아 매매 구간 신호를 생성한다. 화면에 `analysis`, `tradingSignal`, `stockSnapShot`, 점수 퍼센트, ETF 여부, 신호 설명 등이 전달된다.

`StockFacadeAnalyzer`는 Momentum/Technical/Valuation 점수를 합해 100점 기준 점수와 등급을 만든다. 진입 신호는 별도 계산되어 결과에 첨부되며 총점에는 합산되지 않는다. 별도 `TradingSignalAnalyzer`도 Controller에서 실행된다.

### 시장 분석

`MarketAnalysisService.getTotalResult()`가 `MarketDataProvider.fetchGlobalMarketSnapshot()`으로 `Map<IndexType, MarketChartData>` 스냅샷을 한 번 얻고, 12개 분석기를 호출한다. 각 `MarketAnalyzerScore`를 `EngineInput`에 넣어 `MarketRegimeEngine.evaluate()`에 전달한다. `MarketFacadeService.getMarketAnalysisResponse(15)`는 결과와 상위 뉴스 목록을 `MarketDiagnosisService`에 전달해 화면용 진단 응답을 조합한다.

## 7. Analyzer Architecture

주요 Analyzer는 `MarketAnalyzer` 인터페이스를 구현하며 공통 입력은 시장 스냅샷 Map, 출력은 `MarketAnalyzerScore(analyzerName, score, category, rawDetails)`다. 점수 구간은 `>=80` 강한 긍정, `>=40` 긍정, `>-40` 중립, `>-80` 부정, 그 이하는 강한 부정이다.

시장 Analyzer 목록 및 역할:

- `MarketTrendAnalyzer`: SP500/NASDAQ_100/DOW_JONES의 20/50/200일 이동평균 배열, 기울기, 수익률 및 52주 고점 근접도/낙폭을 결합한다. 세 지수 점수 평균. 추세/기울기/수익률/고점·낙폭 요인을 -100..100 범위로 제한한다.
- `MarketMomentumAnalyzer`: 동일 3개 지수에 대해 기간수익률, ROC, RSI, MACD, 모멘텀 가속을 계산하고 유효 데이터가 있는 지수 점수를 평균한다. 120개 미만 차트는 제외한다.
- `MarketBreadthAnalyzer`: `GetStockChartUseCase`로 SPY/RSP, QQQ/QQEW, SPY/IWM 차트를 조회해 20·60일 상대수익률 스프레드를 계산한다. 40/30/30 비중이며 각 spread는 `tanh(spread/4)`로 정규화한다. 각 쌍 최소 61개 데이터가 없으면 해당 spread는 0.
- `RiskAnalyzer`: VIX, HY spread, 달러지수, USD/JPY, Bitcoin의 수준/20일·5일 움직임을 점수화하고 합산 후 -100..100 제한. 입력이 부족한 개별 요소는 0.
- `RateAnalyzer`: 금리 관련 시계열 평가.
- `LiquidityAnalyzer`: 유동성 지표와 `liquidityRegime` 평가.
- `MacroAnalyzer`: 거시 지표와 `macroRegime` 평가.
- `CreditAnalyzer`: 신용 여건과 `creditRegime` 평가.
- `CrossAssetAnalyzer`: 교차 자산 흐름 평가.
- `DivergenceDetector`: 자산/시장 간 괴리와 경고 평가.
- `SectorRotationAnalyzer`: 섹터 강도/리더십(`leadership`) 제공.
- `DefensiveAnalyzer`: 방어 자산/방어 성향 평가.

위 후반 Analyzer의 세부 공식은 각 클래스 구현을 직접 확인해야 하며 이 요약에서는 이름만으로 세부 산식을 단정하지 않는다. 프로젝트에는 `CorporateValueAnalyzer`, `FundamentalAnalyzer`라는 클래스가 확인되지 않았다.

종목 Analyzer:

- `MomentumAnalyzer`: 차트와 현재가로 모멘텀 점수/신호 생성(도메인 최대 30점으로 Controller가 백분율 계산).
- `TechnicalAnalyzer`: 일봉/주봉 가격으로 이동평균 추세, 골든/데드크로스, 변동성을 평가. 일봉 20개 미만이면 0점. 코드 주석의 항목 최대치는 추세 12, 교차 10, 변동성 8.
- `ValuationAnalyzer`: 일반 주식은 기초체력 20 + 밸류에이션 20 = 최대 40. ETF/ETN/VIX ETF/ETN은 별도 자산 모델로 최대 40.
- `EntrySignalAnalyzer`(legacy 패키지): 일/주봉 기반 상세 진입/손절/목표 가격 신호. `StockFacadeAnalyzer`가 진입 신호를 결과에 첨부한다.
- `TradingSignalAnalyzer`: 별도 최신 신호 모델로 차트 구조/레벨 및 `TradingSignalDto`를 계산하며 종목 화면에서 호출된다.
- `SellSignalAnalyzer`: 클래스는 있으나 호출 경로/완성 상태는 확인 필요.

## 8. Scoring & Algorithms

### 종목 점수

`StockFacadeAnalyzer.getScore`: 모멘텀 + 기술 + 밸류에이션. 설계된 영역 최대치는 각각 30 + 30 + 40 = 100점. 등급: S 80+, A 65+, B 48+, C 30+, 그 외 D. 코드에서 점수 입력 범위 초과 clamp 여부는 Analyzer 결과 구현에 따라 확인해야 한다.

`ValuationAnalyzer` 일반 주식:

- 기초체력 최대 20: ROE (가능 점수 5; 15%+ 5, 8%+ 3), 영업이익률 (5; 20%+ 5, 10%+ 3), 매출성장률 (5; 20%+ 5, 10%+ 3), 부채비율 (3; 100% 이하 3), 유동비율 (2; 150%+ 2). 실제 입력된 항목의 가능한 점수 합으로 비율을 내 20점에 scale한다.
- 밸류에이션 최대 20: ROE-PBR 적정성 10 (한국 요구수익률 12, 그 외 10으로 fair PBR 계산; PBR/fairPBR <0.7이면 10, <1.2이면 7), 업종 PER 밴드 7 (하단 미만 7, 하단의 2배 미만 3), PEG 3 (<1이면 3, <1.5이면 1). 결측 항목은 가능한 점수 분모에서 제외 후 비율 환산.
- 결측 재무/밸류 데이터 전체는 해당 파트 0점이며 신호 문자열을 기록.

ETF 등 자산 분석은 시가총액 최대 20점(규모 구간 20/15/10)과 배당수익률 최대 20점(7%+ 20, 4%+ 15, 2%+ 8)을 고정 40점 분모로 합산·정규화한다. 필드 미존재는 획득 점수에 반영되지 않는다.

`TechnicalAnalyzer` 항목 상한은 12+10+8이며, 일봉 부족 시 전체 0점이다. 구체 조건은 `scoreTrend`, `scoreCross`, `scoreVolatility`에 있고 수정 시 최근 정렬 전제와 MA 계산을 확인할 것.

### 시장 점수

`MarketRegimeEngine.getWeightedScore`는 추세 0.15, 모멘텀 0.10, breadth 0.10, risk 0.15, rates 0.10, liquidity 0.10, macro 0.10, credit 0.10, sector 0.05, defensive 0.025, crossAsset 0.025를 곱해 core score를 만든다. 여기에 divergence score * 0.35를 더한 뒤 -100..100으로 clamp한다. confidence는 현재 코드상 `round(abs(weightedScore))`이며 계산된 weightedScore는 `MarketRegimeDto`에 직접 전달되지 않는다.

국면 축은 trend score >=20이면 BULL, <=-20이면 BEAR, 그 사이는 NEUTRAL이며 risk score도 RISK_ON/RISK_OFF/NEUTRAL 동일 경계다. 상승 축일 때 momentum 또는 breadth가 -40 이하이면 국면 설명을 조정한다. RiskAnalyzer 자체는 VIX/HY spread/달러/엔/Bitcoin 조건별 점수를 합산한다.

## 9. Market Analysis

시장 스냅샷은 `MarketDataProvider`가 `MarketIndexHistoryRepository`에서 최근 3년 이력을 한 번에 읽어 `MarketChartData` Map으로 변환한다. 시장 분석 대상에는 미국 주요 지수와 VIX, 신용스프레드, 환율/달러, Bitcoin 등 `IndexType`가 포함된다. 시장 Breadth 분석은 별도 종목 차트 UseCase로 ETF 가격 이력을 얻는다. 지수 대시보드는 `IndexDashboardDto`, 국면 분석은 `MarketRegimeDto`, 진단/뉴스 조합 결과는 `MarketRegimeResponse`로 구분된다.

`market.html`은 `MarketController`가 넣는 `indexDashboard`, `marketAnalysis`, `news`, `loginMember`를 사용한다. Lightweight Charts 4.2.0 기반 지수 차트와 국면 점수/진단, 시장 뉴스 UI가 있다. `market_improved.html`도 존재하지만 현재 Controller 반환값은 `market/market`이다. `IndexDashboardDto` 외에 요청 예시의 `UniverseDashboardDto`, `MarketTrendResult`는 확인되지 않았다.

## 10. External APIs

Provider/설정 코드에서 확인된 연동 그룹:

- 한국투자증권 KIS: `https://openapi.koreainvestment.com:9443` 기본 URL. 토큰 `/oauth2/tokenP`, WebSocket 승인키 `/oauth2/Approval`; 국내/해외 시세, 종목정보, 일봉 차트, 국내 재무/밸류에이션 Provider 존재. 주요 경로 예: 국내 차트 `/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice`, 해외 차트 `/uapi/overseas-price/v1/quotations/dailyprice`, 국내 가격 `/uapi/domestic-stock/v1/quotations/inquire-price`. 인증은 설정된 App Key/Secret과 토큰 매니저 사용.
- FRED: `https://api.stlouisfed.org/fred/series/observations`; series_id, API key, JSON, 최근 3년 관측 기간을 요청한다. `IndexChartOutput1`의 observations를 날짜/close를 갖는 `IndexChartDto` 목록으로 바꿔 `IndexDto`에 담는다.
- Finnhub: base URL `https://finnhub.io/api/v1`; `/stock/metric`의 `metric=all`로 재무 데이터를 받고, `/company-news`(symbol/from/to)와 `/news`(category=general)로 종목/일반 뉴스를 받는다. 인증은 `X-Finnhub-Token`. 재무 DTO는 `FinStockFinancialsOutput`에서 내부 `StockFinancialDto`/`StockFinancialsResponse`로 매핑되고, 뉴스는 `FinStockNewsOutput`에서 `NewsDto`로 변환된다. `fetchEarnings`는 현재 `null` 반환. Rate limit 정책은 코드에서 확인되지 않음.
- Alpha Vantage: base URL `https://www.alphavantage.co`, `/query?function=OVERVIEW&symbol=...&apikey=...`로 해외 밸류에이션 조회. `AlphaValuationOutput`을 `ValuationResponse`로 매핑하며 실패/파싱 오류는 fallback을 반환한다.
- Toss Invest: `https://openapi.tossinvest.com`, 토큰 `/oauth2/token`, 환율 `/api/v1/exchange-rate?baseCurrency=USD&quoteCurrency=KRW`, 시장 캘린더 Provider.
- Gemini: `GeminiAiProvider`, `AiProvider`, 설정된 Google Generative Language API base URL 및 모델 경로를 사용. 인증 키 환경 변수 필요.
- ExchangeRate-API: `https://v6.exchangerate-api.com/v6/{key}/latest/USD` GET. `ExchangeRateOutput`에서 KRW 환산율을 읽어 `ExchangeRateInfo`로 변환하고 실패 시 fallback을 반환한다. 설정 키는 `exchange.auth-key`(환경 변수용 placeholder는 `EXCHANGE_AUTH_KEY`). Toss 환율 Provider도 별도로 존재한다. YahooFinanceAPI / yfinance4j 의존성도 있지만 실제 호출 경로는 별도 확인 필요.

환경 변수/프로퍼티 이름은 14절 참조. `application.yaml`에는 `{KEXIM_AUTH_KEY}`처럼 `${...}` 보간이 아닌 중괄호 값이 일부 남아 있다. 현재 실행 시 값 해석 여부를 설정 클래스와 함께 확인해야 하며 README의 예시를 그대로 신뢰하지 않는다. 코드에서 rate limit 정책 전반은 확인되지 않았으므로 추정해 기록하지 않는다.

## 11. Data Flow

### 종목

`StockController` → `StockSnapshotUseCase.getStockSnapshot(code)` → `StockSnapshot`(info, price, daily/weekly charts, financial, valuation 등) → `StockAnalysisService` → `StockFacadeAnalyzer` → `MomentumScore` + `TechnicalScore` + `ValuationScore` + `EntrySignal` → `StockAnalysisResult` → Model → `templates/stock/analysis.html`.

동시에 Controller가 `MarketContext.of(dailyCharts, weeklyCharts)`를 만들어 `TradingSignalAnalyzer`를 실행하고 `tradingSignal`로 뷰에 전달한다. 따라서 두 신호 모델을 하나로 간주하지 않는다.

### 시장

`MarketController` → `MarketFacadeService` → (a) 시장 지수 대시보드, (b) `MarketAnalysisService`의 단일 시장 snapshot → 12 Analyzer → `EngineInput` → `MarketRegimeEngine` → regime DTO → `MarketDiagnosisService` + 최신 뉴스 → `MarketRegimeResponse`, (c) 페이징 뉴스 → Model → `market/market.html`.

저장형 지수 조회/갱신 경로로 `IndexService`, `MarketIndexQueryService`, `MarketIndexRefreshService` 등이 병행 존재한다. 이들 간 책임 이전은 TODO가 남아 있으므로 한 흐름으로 통합되었다고 가정하지 않는다.

## 12. Backend Structure

페이지 Controller: Auth, Friend, Main, Market, Portfolio, Stock, Trading. API Controller: MarketApi, PortfolioApi, StockApi, TradingApi. Services는 기능별 조회/저장/조합 담당이며 Repository는 Spring Data JPA 패턴을 따른다. 외부 API 응답은 `infra.*.dto`로 받고 모듈의 Provider 포트를 통해 내부 DTO/도메인으로 전달한다.

공통 기반: `RestTemplateConfig`, `WebClientConfig`, `JacksonConfig`, `WebConfig`, `LoginCheckInterceptor`, `LoginUserArgumentResolver`, `GlobalExceptionHandler`, `BusinessException/ErrorCode`, `LoggingAspect`, `RetryAspect`. 실제 보안은 커스텀 로그인 세션/인터셉터와 비밀번호 해시용 Spring Security Crypto 코드가 중심이며 Spring Security 웹 스타터는 선언되지 않았다.

## 13. Frontend Structure

- 페이지: `main/main.html`, `market/market.html`, `market/market_improved.html`, `stock/analysis.html`, `stock/analysisLegacy.html`, `portfolio/portfolio.html`, `portfolio/tradelog.html`, auth/friends/community 및 오류 페이지.
- 공통 레이아웃 `layouts/default_layout.html`, `fragments/header.html`, `sidebar.html`; 개선 버전 fragment도 별도로 존재한다. 모든 화면이 공통 레이아웃을 사용하는 것은 아니며 페이지별 CDN/스타일 포함이 혼재한다.
- `market.html`: `loginMember`, `indexDashboard`, `marketAnalysis`, `news`를 소비하고 여러 지수 탭/Lightweight Charts 차트, 시장 분석 카드 및 뉴스 영역을 렌더한다. 브라우저 JS의 `marketDashboardApp`이 탭과 차트 상태를 관리한다.
- Alpine.js `x-data`, 이벤트 바인딩과 Bootstrap/Bootstrap Icons CDN 사용이 확인된다. Chart.js는 `friend_portfolio.html` 및 default layout에서 확인된다. 정적 스타일은 `static/css/custom.css`; 화면별 인라인 CSS/JS도 있다.
- 차트 라이브러리 간 통합 빌드/패키지 관리 체계는 확인되지 않는다. `market_improved`와 `analysisLegacy` 파일은 존재하지만 활성 Controller 경로와 구분할 것.

## 14. Configuration

`src/main/resources/application.yaml`은 기본 설정이다.

- 기본 DB: `jdbc:mysql://localhost:3306/stocklog`, 드라이버 `com.mysql.cj.jdbc.Driver`, username `root`, password `${MYSQL_PASSWORD}`, Hibernate `ddl-auto: update`, MySQL dialect.
- Thymeleaf UTF-8 및 cache false. 서버 세션 tracking은 cookie. P6Spy SQL 로깅 활성화.
- KIS: `KIS_APP_KEY`, `KIS_APP_SECRET`; 기본 URL은 설정 파일에 명시.
- Gemini: `GEMINI_API_KEY` 및 base URL/model 설정.
- FRED: `FRED_API_KEY`; Alpha: `ALPHA_API_KEY`.
- YAML에는 KEXIM_AUTH_KEY, TWELVE_AUTH_KEY, EXCHANGE_AUTH_KEY, FINNHUB_API_KEY/FINNHUB_SECRET, TOSS_API_KEY/TOSS_SECRET 자리 값도 있다. 이 값들은 환경 변수 보간 문법인지 실제 코드가 기대하는 설정인지 추가 확인 필요.
- `application-test.yml`: H2 `jdbc:h2:mem:testdb;MODE=MySQL`, `ddl-auto:create-drop`.
- 별도의 `application-local.yaml`은 현재 `src/main/resources`에서 확인되지 않았다. Spring profile 설정 및 API별 로컬 override도 제한적으로만 확인됨.
- 비밀값은 저장소 문서에 기록하지 않는다. `.gitignore`, 설정 클래스의 `@ConfigurationProperties` prefix와 함께 실제 환경에서 주입이 필요한 변수 이름을 확인할 것.

## 15. Running & Testing

저장소에 Gradle Wrapper가 있다.

- Windows 실행: `.\gradlew.bat bootRun`
- 빌드: `.\gradlew.bat build`
- 테스트: `.\gradlew.bat test`
- 테스트 프로필 자동 적용 여부는 개별 테스트 설정을 확인할 것. `application-test.yml`은 존재한다.
- 기본 실행에 MySQL 로컬 인스턴스/`stocklog` DB와 `MYSQL_PASSWORD` 설정이 필요하다. 외부 Provider를 쓰는 기능에는 해당 API 자격 증명도 필요하다. CSV 초기 데이터 파일이 `src/main/resources/data`에 포함된다.
- 이번 문서 작업에서는 빌드/테스트를 실행하지 않았다.

## 16. Implementation Status

### 구현되어 코드 경로가 연결된 기능

- 세션 기반 회원 가입/로그인/로그아웃 및 회원 정보 Controller 경로, 친구 요청/수락/거절/조회.
- 홈 대시보드 페이지, 시장 페이지, 종목 분석 페이지, 포트폴리오/거래 관련 페이지와 API Controller.
- 종목 정보/가격/차트/재무/밸류에이션 Entity·Provider·Service 구조 및 종목 snapshot과 분석 결과 생성.
- 시장 index/chart/환율/news 데이터 구조, 시장 Analyzer 12개와 국면/진단 DTO 조합.
- KIS/FRED/Finnhub/Alpha/Toss/Gemini 등의 외부 Adapter/config 일부.
- CSV/종목 마스터 초기화 관련 Runner와 파서 코드.

### 부분 구현 또는 경로별 확인이 필요한 기능

- 홈 `MainDashboardService`/`MainDashboardUseCase`, 시장 index query/refresh 유스케이스는 구현 클래스가 있으나 코드 주석에 기존 Controller/Service 조합 책임 이동 TODO가 남아있다.
- 시장 진단 Entity/History와 서비스가 존재하지만 모든 진단 실행이 이력 저장까지 수행하는지 일괄 확인되지 않았다.
- 종목 API Controller에는 분석/AI comment 위임 TODO가 있으나 페이지 분석은 별도 Controller 경로로 연결되어 있다.
- `SellSignalAnalyzer`, 개선된 템플릿/fragment, legacy 분석 코드 등은 파일은 있으나 기본 화면 경로의 활성 여부가 제한적이다.
- README는 local 프로필과 API 키 사용 예를 설명하지만 실제 local 설정 파일이 없고 YAML 일부 placeholder 형식이 일치하지 않는다.

### 예정/미구현으로 확인되는 항목

- 코드 TODO에 명시된 매도 검증 반환 로직(Portfolio API), `STOCK_SCORE` 규칙 처리.
- 홈 대시보드 조합 책임 이전, 시장 조회/갱신 UseCase 분리 및 스케줄/비동기 실행 결정.
- JPA 운영 스키마 migration 도입은 설정 주석상 제안일 뿐 Flyway/Liquibase 의존성/설정은 없다.
- 실제 외부 호출 경로가 없는 기능은 Provider 클래스 존재만으로 완료 판정하지 않는다.

## 17. TODO & Technical Debt

- `application.yaml`은 `ddl-auto:update`이며 주석에서 운영 스키마 관리 위험과 migration 도입을 언급한다. OSIV 설정은 주석 처리되어 명시적 운영 방침이 확인되지 않는다.
- API 키 placeholder 표기가 혼재한다. `${...}` 형태와 `{...}` 형태가 함께 있어 환경별 바인딩 검증이 필요하다.
- `NewsService`에 뉴스 페이징 TODO가 있지만 Controller는 Pageable을 받는다. 서비스 내부 실제 페이징 적용 여부 확인 후 수정할 것.
- `PortfolioApiController`의 매도 검증은 TODO로 표시. `TradingRuleService`에는 `STOCK_SCORE` TODO.
- `MainDashboardService`, `MainDashboardUseCase`, Market Index Query/Refresh 경계에서 책임 이전 TODO 다수.
- `StockApiController`에는 Controller 업무 위임 TODO가 있고, 페이지 분석 경로와 API 분석 경로가 다르다.
- legacy/개선 템플릿, `EntrySignalAnalyzer` legacy 패키지 등 중복 구현 흔적이 있다. 삭제 전에 참조 검색 필요.
- Holding 평균 매입가 동시 수정 TODO가 있다. PortfolioService 주석은 EntityGraph로 holding→stock 조회 N+1을 피한다고 언급하고, DTO→Entity 생성 시 영속성 참조 주의가 필요하다.
- API rate limit, 재시도/백오프의 전체 적용 여부 및 장애 시 fallback은 확인되지 않았다. `RetryAspect` 존재만으로 모든 Provider에 적용된다고 가정하지 않는다.
- 테스트 파일 수가 주요 업무 소스 규모에 비해 제한적이다. 이것은 관찰이며 미검증 결함을 의미하지 않는다.

## 18. Coding Conventions

- Java 패키지는 기능별 하위 패키지로 나누고 클래스는 역할 suffix(`Controller`, `Service`, `Repository`, `Provider`, `Dto`, `Response`, `Analyzer`)를 사용한다.
- 생성자 주입에 Lombok `@RequiredArgsConstructor`를 자주 사용한다. 엔티티/DTO에 Lombok도 사용한다.
- Spring Data JPA repository와 `@Service`/`@Component`를 활용한다. 외부 Provider는 모듈 포트 인터페이스로 추상화하려는 패턴이 있다.
- 일부 결과 타입은 Java record를 사용한다. 외부 API 전용 DTO와 내부 응답 DTO를 구분한다.
- Controller는 Model attribute를 구성하고 템플릿 이름을 반환한다. 로그인 사용자 전달에는 `@LoginUser` 커스텀 인자 resolver 사용.
- 업무 오류는 `BusinessException`, `ErrorCode`, `GlobalExceptionHandler` 경로를 가진다. 로깅에는 SLF4J/Lombok `@Slf4j` 사용.
- Analyzer는 계산 결과와 설명용 signals/details를 함께 반환하는 경향이 있다. 기존 점수와 메시지 계약을 함께 검토할 것.

## 19. Important Constraints

- 화면 계약: `market.html`의 `indexDashboard`, `marketAnalysis`, `news`; `stock/analysis.html`의 `analysis`, `tradingSignal`, `stockSnapShot`, `isEtf`, `signalDescMap` 등의 Model 이름은 Controller와 템플릿이 결합되어 있으므로 동시 변경 없이 이름을 바꾸지 않는다.
- 종목 총점은 세 영역 점수 합산이며 entry signal은 부가 정보다. 이를 총점에 넣거나 기존 배점을 바꾸면 등급/화면 퍼센트 의미가 달라진다.
- 시장 regime 입력은 12개 Analyzer 결과다. 가중치와 trend/risk 축 경계(±20), divergence 조정은 화면 진단에 직접 영향이 있다.
- 외부 응답 DTO와 KIS 인증/거래 ID, URI 파라미터는 Provider 계약이다. 실제 코드나 공급자 명세 확인 없이 변경 금지.
- DB Entity/복합키, 기존 테이블명/컬럼 및 `valutation` 패키지 오타도 현 코드 계약이다.
- 비로그인 접근 동작, URL, API 경로, 페이지 템플릿 간 차이는 Controller/인터셉터 기반으로 유지 검토.

## 20. Claude Development Guidelines

1. 수정 전에 관련 Controller → Service/UseCase → Provider/Repository → DTO/Entity → 템플릿의 기존 흐름을 읽는다.
2. 현재 구조와 역할 분리를 파악한 뒤 변경한다. TODO 메모를 현재 동작으로 오인하지 않는다.
3. 기존 기능을 임의로 삭제하지 않는다. 참조 및 활성 URL을 먼저 검색한다.
4. 구현되지 않은 기능을 구현된 것으로 가정하지 않고, 확인되지 않은 내용은 명시한다.
5. API 응답, 인증 방식, endpoint 및 파라미터를 추측하지 않는다.
6. Analyzer 책임을 임의로 합치거나 분리하지 않는다.
7. 점수 계산 변경 시 기존 공식/분모/결측 처리/경계값과 변경 이유를 설명하고 관련 화면 표시 영향을 확인한다.
8. Backend DTO 변경은 해당 Thymeleaf 변수 및 JavaScript 소비 코드까지 추적한다.
9. Controller Model attribute와 URL/API 계약을 연쇄 확인 없이 이름 변경하지 않는다.
10. 변경 전 클래스명·메서드·DTO 사용처를 저장소 전체에서 검색한다.
11. 가능한 경우 변경 후 테스트/빌드를 실행하고, 실행하지 못한 경우 이유를 기록한다.
12. 완료 보고에서 바뀐 파일과 변경 내용을 요약한다.
13. Entity/schema 변경은 `ddl-auto:update`의 운영 위험과 기존 데이터 호환성을 고려한다.
14. `market_improved.html`, legacy 분석기, API Controller와 페이지 Controller의 중복 경로는 실제 활성 연결을 입증하기 전까지 정리/삭제하지 않는다.

