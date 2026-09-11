# StockLog

주식 투자 기록, 포트폴리오 관리, 시장 정보 조회를 한곳에서 지원하는 개인 투자 대시보드입니다.  
국내·해외 주식 시세와 재무 데이터를 바탕으로 종목을 분석하고, 매매 기록과 투자 원칙을 함께 관리하는 것을 목표로 합니다.

> 이 프로젝트는 개인 학습 및 포트폴리오 용도로 개발 중입니다. 제공되는 분석 정보는 투자 권유가 아니며, 투자 판단과 책임은 사용자에게 있습니다.

## 주요 기능

- 회원가입·로그인 및 회원 정보 관리
- 친구 추가 및 친구 포트폴리오 조회
- 보유 종목, 관심 종목, 포트폴리오 비중 관리
- 매수·매도 기록과 월별 거래 내역 조회
- 투자 원칙 등록 및 거래 전 원칙 점검
- 국내·해외 주식 시세, 재무 정보, 차트 기반 종목 분석
- 밸류에이션·모멘텀·기술적 지표를 조합한 종목 점수 및 등급 제공
- 국내외 시장 지수, 환율, 시장 뉴스 대시보드
- AI 기반 시장 분석

## 기술 스택

| 구분 | 사용 기술 |
| --- | --- |
| Backend | Java 17, Spring Boot, Spring MVC |
| Persistence | Spring Data JPA, H2, MySQL |
| View | Thymeleaf |
| Build | Gradle |
| Utilities | Lombok, OpenCSV, Jackson |
| External APIs | 한국투자증권 Open API, Finnhub, FRED, Alpha Vantage, Gemini, 환율 API 등 |

## 프로젝트 구조

```text
src/main/java/com/stocklog
├── application     # 대시보드 유스케이스·서비스
├── global          # 공통 설정, 예외 처리, AOP, 인터셉터
├── infra           # 외부 API 연동 구현체
├── modules
│   ├── market      # 시장 지수, 환율, 뉴스, 시장 분석
│   ├── member      # 회원·친구 관계
│   ├── purchase    # 포트폴리오·매매 기록·투자 원칙
│   └── stock       # 종목 정보·분석
└── web             # MVC 컨트롤러
```

## 시작하기

### 요구 사항

- JDK 17
- 인터넷 연결 및 외부 API 키
- 선택: MySQL (기본 설정은 파일 기반 H2)

### 실행

```bash
git clone https://github.com/<your-id>/stock-log.git
cd stock-log
./gradlew bootRun
```

Windows에서는 아래 명령을 사용합니다.

```powershell
.\gradlew.bat bootRun
```

실행 후 [http://localhost:8080](http://localhost:8080)에서 확인할 수 있습니다. H2 콘솔은 [http://localhost:8080/h2-console](http://localhost:8080/h2-console)에서 열 수 있습니다.

## API 키 설정

외부 서비스 연동에는 API 키가 필요합니다. 키는 절대 저장소에 커밋하지 마세요.

현재 `src/main/resources/application.yaml`에는 다음 연동 설정이 있습니다.

- KIS: `KIS_APP_KEY`, `KIS_APP_SECRET`
- KEXIM / Twelve Data / Exchange Rate
- Gemini
- Finnhub
- FRED
- Alpha Vantage
- Toss

개발 환경에서는 `application-local.yaml`을 만들고 `.gitignore`에 추가해 개인 키를 관리하는 방식을 권장합니다. 예시는 다음과 같습니다.

```yaml
kis:
  app-key: ${KIS_APP_KEY}
  app-secret: ${KIS_APP_SECRET}

gemini:
  api:
    key: ${GEMINI_API_KEY}

finnhub:
  api:
    key: ${FINNHUB_API_KEY}
    secret: ${FINNHUB_SECRET}
```

환경 변수를 설정한 뒤 로컬 프로필로 실행합니다.

```powershell
$env:KIS_APP_KEY = "..."
$env:KIS_APP_SECRET = "..."
$env:GEMINI_API_KEY = "..."
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

> `application.yaml`에 남아 있는 `{..._API_KEY}` 형태의 값도 실제 키를 넣기보다 `${..._API_KEY}` 형태의 환경 변수 참조로 변경해 사용하는 것을 권장합니다.

## 테스트

```bash
./gradlew test
```

## 향후 개선 방향

- 프로필별 데이터베이스 및 API 설정 분리
- Flyway 또는 Liquibase 기반 DB 마이그레이션 도입
- 외부 API 장애 대응 및 캐시 정책 보강
- 테스트 범위 확대 및 CI 구축
- Docker 기반 실행 환경 제공

## License

별도 라이선스가 아직 지정되지 않았습니다. 공개 범위와 사용 조건을 정한 뒤 라이선스 파일을 추가할 예정입니다.
