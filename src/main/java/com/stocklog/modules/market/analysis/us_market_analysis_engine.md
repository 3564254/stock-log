# 미국 시장 분석기 설계

## 1. 목적

개별 종목의 매매 신호를 만드는 것이 아니라, **미국 주식시장 전체의 국면(Market Regime)** 을 판단하는 분석 시스템을 구축한다.

최종적으로 다음을 판단한다.

- 시장 추세: Bull / Bear
- Risk Appetite: Risk-On / Risk-Off
- 경기 사이클: Expansion / Slowdown / Contraction / Recovery
- 유동성: Expanding / Neutral / Contracting
- 신용환경: Healthy / Stress
- 섹터 리더십: 어떤 섹터가 시장을 주도하는가
- 자산 간 Divergence
- 최종 Market Regime
- Confidence Score

---

# 2. 전체 구조

```text
                         Market Analysis System
                                  │
        ┌─────────────────────────┼─────────────────────────┐
        │                         │                         │
        ▼                         ▼                         ▼
  Market / Price             Macro / Liquidity        Cross Asset
        │                         │                         │
        ├─ Trend                  ├─ Rates                ├─ CrossAsset
        ├─ Momentum               ├─ Liquidity             └─ Divergence
        ├─ Breadth                ├─ Macro
        └─ Risk                   └─ Credit
                                  │
                                  ▼
                         Sector / Defensive
                                  │
                    ┌─────────────┴─────────────┐
                    ▼                           ▼
             Sector Rotation             Defensive
                    │                           │
                    └─────────────┬─────────────┘
                                  ▼
                         MarketRegimeEngine
                                  │
                                  ▼
                       ┌─────────────────────┐
                       │ Final Market Regime │
                       ├─────────────────────┤
                       │ Trend               │
                       │ Risk                 │
                       │ Macro                │
                       │ Liquidity            │
                       │ Credit               │
                       │ Leadership           │
                       │ Confidence           │
                       └─────────────────────┘
```

---

# 3. 분석기 목록

| # | Analyzer | 핵심 역할 |
|---|---|---|
| 1 | `MarketTrendAnalyzer` | 시장의 중장기 추세 판단 |
| 2 | `MarketMomentumAnalyzer` | 시장 상승/하락 모멘텀 판단 |
| 3 | `MarketBreadthAnalyzer` | 시장 내부 참여도와 확산 정도 판단 |
| 4 | `RiskAnalyzer` | Risk-On / Risk-Off 판단 |
| 5 | `RateAnalyzer` | 금리와 Yield Curve 환경 판단 |
| 6 | `LiquidityAnalyzer` | 금융시장 유동성 환경 판단 |
| 7 | `MacroAnalyzer` | 경기 사이클 판단 |
| 8 | `CreditAnalyzer` | 신용시장 건전성 판단 |
| 9 | `SectorRotationAnalyzer` | 섹터별 자금 이동과 리더십 판단 |
| 10 | `DefensiveAnalyzer` | 방어적 자산 선호도 판단 |
| 11 | `CrossAssetAnalyzer` | 주식/채권/달러/원자재 관계 분석 |
| 12 | `MarketRegimeEngine` | 모든 분석기를 종합하여 최종 국면 판정 |

> **핵심:** 각 Analyzer는 서로 다른 역할을 담당하고, 최종 판단은 `MarketRegimeEngine`에서 수행한다.

---

# 4. MarketTrendAnalyzer

## 목적

S&P 500, Nasdaq 100, Dow Jones의 **중장기 추세**를 판단한다.

## 입력

- `SP500`
- `NASDAQ_100`
- `DOW_JONES`

## 분석 항목

```text
MA20
MA50
MA200

Price / MA20
Price / MA50
Price / MA200

MA20 slope
MA50 slope
MA200 slope

20D Return
60D Return
120D Return
200D Return

Drawdown
52W High proximity
```

## 결과

```text
STRONG_BULL
BULL
NEUTRAL
BEAR
STRONG_BEAR
```

## 핵심

단순히 현재 가격이 상승했는지만 보지 않는다.

**가격 + 이동평균 + 이동평균 방향 + 장기 수익률**을 함께 사용한다.

---

# 5. MarketMomentumAnalyzer

## 목적

현재 시장의 **상승/하락 힘이 강화되고 있는지** 판단한다.

## 분석 항목

```text
1D Return
5D Return
20D Return
60D Return
120D Return

ROC
RSI
MACD
Momentum Acceleration
```

## 중요한 개념

### Momentum

```text
현재 상승률
```

### Momentum Acceleration

```text
현재 Momentum - 과거 Momentum
```

예:

```text
20D Return +5%
→ +8%
→ +12%

= Momentum 강화
```

반대로

```text
20D Return +12%
→ +8%
→ +3%

= Momentum 약화
```

---

# 6. MarketBreadthAnalyzer

## 목적

시장 상승이 **소수 대형주만의 상승인지, 시장 전체가 참여하는 상승인지** 판단한다.

현재 변수 목록에는 Breadth 데이터가 부족하므로 향후 별도 데이터 소스를 추가하는 것을 권장한다.

## 권장 데이터

```text
Advance / Decline
Advancing Stocks %
Declining Stocks %
52W New High
52W New Low
Stocks Above MA50
Stocks Above MA200
Equal Weight vs Cap Weight
```

## 핵심 신호

```text
S&P500 상승
+
Breadth 확대
= Healthy Rally
```

반대로

```text
S&P500 상승
+
Breadth 축소
= Narrow Rally
= 경고
```

---

# 7. RiskAnalyzer

## 목적

시장의 Risk-On / Risk-Off 상태를 판단한다.

## 입력

- `VIX`
- `HIGH_YIELD_SPREAD`
- `USD_JPY`
- `DOLLAR_INDEX`
- `BITCOIN`

## 분석

```text
VIX Level
VIX Δ5D
VIX Δ20D

HY Spread Level
HY Spread Δ5D
HY Spread Δ20D

USD/JPY Trend
Dollar Index Trend
Bitcoin Momentum
```

## 결과

```text
EXTREME_RISK_ON
RISK_ON
NEUTRAL
RISK_OFF
EXTREME_RISK_OFF
```

## 예시

```text
VIX ↓
HY Spread ↓
USD/JPY ↑
주가지수 ↑

→ Risk-On 강화
```

---

# 8. RateAnalyzer

## 목적

금리 수준과 금리 변화가 주식시장에 어떤 영향을 주는지 판단한다.

## 입력

- `US_TREASURY_10Y`
- `US_TREASURY_2Y`
- `T10Y2Y`
- `US_FED_FUNDS_RATE`

## 분석

```text
10Y Level
10Y Δ5D
10Y Δ20D

2Y Level
2Y Δ5D
2Y Δ20D

10Y - 2Y
Yield Curve 변화
Fed Funds Rate
```

## 중요

금리의 절대값만 보지 않는다.

**수준 + 방향 + 변화속도**를 함께 본다.

## 예시

```text
10Y ↑
NASDAQ ↑

→ 시장이 금리 상승을 감내하는 상태
```

```text
10Y ↑↑
NASDAQ ↓↓

→ 금리 상승이 Growth valuation을 압박
```

---

# 9. LiquidityAnalyzer

## 목적

시장에 공급되는 **금융 유동성의 방향**을 판단한다.

## 입력

- `FED_TOTAL_ASSETS`
- `REVERSE_REPO`
- `US_M2`
- `US_FED_FUNDS_RATE`

## 분석

```text
Fed Balance Sheet
M2
ON RRP
Fed Funds Rate
```

그리고 단순 레벨보다 변화량을 계산한다.

```text
Liquidity Level
Liquidity Momentum
Liquidity Acceleration
```

## 개념

```text
Fed Assets ↑
M2 ↑
RRP ↓

→ 유동성 개선 가능성
```

반대로

```text
Fed Assets ↓
M2 둔화
RRP 증가

→ 유동성 악화 가능성
```

---

# 10. MacroAnalyzer

## 목적

미국 경제의 경기 사이클을 판단한다.

## 입력

### 물가

- `US_CPI`
- `US_CORE_PCE`

### 고용

- `US_UNEMPLOYMENT_RATE`
- `US_NONFARM_PAYROLLS`
- `US_INITIAL_CLAIMS`

### 경기/소비

- `US_GDP`
- `US_RETAIL_SALES`
- `US_CONSUMER_SENTIMENT`
- `US_INDPRO`

## 결과

```text
EXPANSION
LATE_CYCLE
SLOWDOWN
CONTRACTION
RECOVERY
```

## 핵심

절대적인 GDP/CPI 값보다 **추세와 변화 방향**을 중요하게 본다.

예:

```text
GDP 성장 둔화
Retail Sales 둔화
Industrial Production 둔화
Initial Claims 상승

→ Slowdown
```

---

# 11. CreditAnalyzer

## 목적

신용시장의 스트레스 여부를 판단한다.

## 입력

- `HIGH_YIELD_SPREAD`
- `FINANCIAL_STRESS`
- `US_CONSUMER_REVOLVING_LOANS`
- `SECTOR_FINANCIALS`

## 핵심

신용시장은 주식보다 먼저 위험 신호를 보여줄 수 있다.

## 예시

```text
S&P500 ↑
VIX ↓
HY Spread ↑↑

→ 표면적으로 Risk-On
→ 내부 Credit Stress 발생
→ Warning
```

이런 **Credit Divergence**를 별도로 기록한다.

---

# 12. SectorRotationAnalyzer

## 목적

시장 자금이 어느 산업/테마로 이동하고 있는지 판단한다.

## 입력

### Growth / AI

- `PHLX_SEMI`
- `NASDAQ_AI_BIGDATA`
- `NASDAQ_CAPEX_ACHIEVERS`

### Cyclical

- `SECTOR_INDUSTRIALS`
- `SECTOR_DISCRETIONARY_DURABLE`
- `SECTOR_FINANCIALS`

### Defensive

- `DOW_JONES_UTILITY`
- `NASDAQ_CONSUMER_STAPLES`

### 기타

- `NASDAQ_ISE_CYBERSECURITY`
- `NASDAQ_BIOTECH`
- `NASDAQ_CLEAN_ENERGY`
- `PHLX_GOLD_SILVER`
- `SECTOR_REAL_ESTATE`

## 핵심

각 섹터의 절대 수익률보다 **시장 대비 상대강도(Relative Strength)** 를 계산하는 것이 중요하다.

```text
Sector Return
    -
S&P500 Return
    =
Relative Strength
```

## 결과 예

```text
Leadership:
1. Semiconductor
2. AI
3. Industrials

Weakness:
1. Utilities
2. Staples
```

---

# 13. DefensiveAnalyzer

## 목적

시장이 방어적 포지션으로 이동하는지 판단한다.

## 입력

- `DOW_JONES_UTILITY`
- `NASDAQ_CONSUMER_STAPLES`
- `PHLX_GOLD_SILVER`
- `DOLLAR_INDEX`
- `US_TREASURY_10Y`

## 핵심

```text
Utilities ↑
Staples ↑
Gold ↑
Dollar ↑
Growth ↓

→ Defensive Rotation
```

특히 주식시장이 상승하는데 방어자산도 동시에 강해지는 경우를 **경계 신호**로 기록한다.

---

# 14. CrossAssetAnalyzer

## 목적

각 자산군의 움직임이 서로 일치하는지 분석한다.

## 분석 대상

```text
Stocks
Bonds / Yields
Dollar
Oil
Gold
Bitcoin
```

## 분석 예

```text
Stocks ↑
Yields ↑
Dollar ↑
Oil ↑

```

또는

```text
Stocks ↓
VIX ↑
HY Spread ↑
Dollar ↑
Gold ↑
```

처럼 여러 자산의 움직임을 동시에 판단한다.

---

# 15. DivergenceDetector

`CrossAssetAnalyzer` 내부 또는 별도 모듈로 구현한다.

## 핵심 목적

**가격과 위험지표가 서로 다른 방향으로 움직이는 상황**을 찾는다.

## 예

### Risk Divergence

```text
S&P500 ↑
VIX ↑

→ Risk 경고
```

### Credit Divergence

```text
S&P500 ↑
HY Spread ↑

→ Credit Stress
```

### Leadership Divergence

```text
NASDAQ ↑
SOX ↓

→ Tech Leadership 약화
```

### Breadth Divergence

```text
S&P500 신고가
Breadth 감소

→ Narrow Market
```

---

# 16. 공통 Score 체계

각 Analyzer의 결과는 서로 비교할 수 있도록 동일한 범위로 정규화한다.

권장:

```text
-100 ~ +100
```

의미:

```text
+80 ~ +100 : Strong Positive
+40 ~ +79  : Positive
-39 ~ +39  : Neutral
-79 ~ -40  : Negative
-100 ~ -80 : Strong Negative
```

예:

```text
Trend       +80
Momentum    +65
Breadth     +72
Risk        +70
Rates       +20
Liquidity   +55
Macro       -15
Credit      +70
Sector      +90
Defensive   -40
CrossAsset  +60
```

---

# 17. MarketRegimeEngine

모든 Analyzer의 결과를 종합한다.

## 기본 가중치 예시

```text
Trend              15%
Momentum           10%
Breadth             10%
Risk                15%
Rates               10%
Liquidity           10%
Macro               10%
Credit              10%
Sector Rotation      5%
Defensive            2.5%
Cross Asset          2.5%
```

총합:

```text
100%
```

> 가중치는 초기값이며 백테스트를 통해 최적화해야 한다.

---

# 18. 최종 Regime

최종 Score 예시:

```text
+60 ~ +100
→ BULL / RISK-ON

+20 ~ +59
→ BULLISH / NEUTRAL

-19 ~ +19
→ NEUTRAL / TRANSITION

-59 ~ -20
→ BEARISH / RISK-OFF

-100 ~ -60
→ BEAR / EXTREME RISK-OFF
```

단, **하나의 Score만으로 최종 국면을 결정하지 않는 것**을 권장한다.

---

# 19. 2차원 Market Regime

가장 추천하는 방식이다.

## Axis 1: Trend

```text
Bear ←────────────→ Bull
```

## Axis 2: Risk Appetite

```text
Risk-Off
   ↑
   │
   │
   │
   ↓
Risk-On
```

이를 이용해 시장을 다음과 같이 분류한다.

```text
                 Trend
             Bear       Bull
              │          │
Risk-Off ─────┼──────────┼────
              │          │
              │          │
Risk-On  ─────┼──────────┼────
              │          │
```

대표적인 국면:

```text
BULL + RISK-ON
→ 강한 상승장

BULL + RISK-OFF
→ 상승 추세지만 위험 증가

BEAR + RISK-ON
→ 반등 / Recovery 가능성

BEAR + RISK-OFF
→ 강한 하락장
```

---

# 20. 최종 출력 구조

최종 엔진은 단순히 `BULL` 하나만 반환하지 않는다.

권장 결과:

```text
MARKET REGIME
────────────────────────

Regime      : BULL
Risk        : RISK-ON
Trend       : STRONG_BULL
Macro       : SLOWDOWN
Liquidity   : EXPANDING
Credit      : HEALTHY

Leadership
────────────────────────
1. Semiconductor
2. AI
3. Industrials

Warnings
────────────────────────
- Macro slowdown
- 10Y yield rising

Divergence
────────────────────────
- None

Confidence
────────────────────────
78 / 100
```

---

# 21. 추천 클래스 구조

```text
MarketAnalysisEngine
│
├── MarketTrendAnalyzer
├── MarketMomentumAnalyzer
├── MarketBreadthAnalyzer
├── RiskAnalyzer
├── RateAnalyzer
├── LiquidityAnalyzer
├── MacroAnalyzer
├── CreditAnalyzer
├── SectorRotationAnalyzer
├── DefensiveAnalyzer
├── CrossAssetAnalyzer
│
├── DivergenceDetector
│
└── MarketRegimeEngine
```

---

# 22. 데이터 처리 Pipeline

```text
FRED / Market Data
        ↓
Raw Data Collector
        ↓
Data Normalizer
        ↓
Missing Data / Frequency 처리
        ↓
Feature Calculator
        ↓
각 Analyzer
        ↓
Factor Score (-100 ~ +100)
        ↓
Divergence Detection
        ↓
MarketRegimeEngine
        ↓
Final Market Regime
```

---

# 23. 중요한 설계 원칙

## ① 서로 다른 Period를 직접 비교하지 않는다

```text
Daily
Weekly
Monthly
Quarterly
```

데이터를 그대로 합산하지 않는다.

먼저 변화율, Trend, Z-Score 등으로 변환한 후 공통 Score로 만든다.

---

## ② Level보다 변화 방향을 중요하게 본다

예:

```text
VIX = 20
```

만으로 판단하지 않는다.

```text
VIX = 20
5D 변화 = +30%
20D 변화 = +15%
```

처럼 방향과 속도를 같이 본다.

---

## ③ 절대 수익률과 상대 수익률을 분리한다

섹터 분석에서는

```text
Sector Return
```

과

```text
Sector Return - Market Return
```

을 모두 계산한다.

---

## ④ Divergence를 1급 신호로 취급한다

시장이 상승하는데

```text
Breadth ↓
Credit ↓
SOX ↓
VIX ↑
```

같은 현상이 발생하면 단순 Bull Score를 그대로 유지하지 않고 **Regime Warning**을 발생시킨다.

---

## ⑤ 최종 Score와 Confidence를 분리한다

예:

```text
Score      = +72
Confidence = 54
```

가능하다.

Score는 상승 방향이지만 지표 간 의견이 서로 다르면 Confidence를 낮춘다.

---

# 24. 최종 목표

최종 시스템은 단순한

```text
"주식이 올랐다 → Bull"
```

이 아니라,

```text
Price Trend
      +
Momentum
      +
Breadth
      +
Risk Appetite
      +
Rates
      +
Liquidity
      +
Macro
      +
Credit
      +
Sector Leadership
      +
Cross Asset
      +
Divergence
      ↓
Market Regime
      +
Confidence
      +
Warning
```

을 출력하는 **미국 시장 국면 분석기**로 설계한다.
