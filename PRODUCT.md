# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

The author and a small circle of friends, all Korean retail investors trading domestic (KRX) and US stocks. They open StockLog before deciding on a trade, after a trade to record it, and periodically to review their holdings and the market. The friends are also users who can view each other's portfolios.

## Product Purpose

StockLog is a personal investing journal and dashboard that brings together analysis, trade records, and investment discipline in one place. Success means that a user can understand a stock and the market, check a planned trade against their own rules, and keep an honest record of what they did, without switching between a brokerage app, data sites, and notes.

## Positioning

Brokerage apps such as Toss and KIS are built to execute orders. StockLog sits before and after execution and does not place orders:

- **Unified analysis.** Valuation, momentum, and technical scores are combined into one stock grade, alongside market phase diagnosis, indices, FX, news, and an AI market read, so a decision draws on one consistent view.
- **Discipline before trades.** Users write their own investment rules, and StockLog checks a planned buy or sell against those rules before it is logged.

## Operating Context

- Live and reference data come from external APIs: 한국투자증권 (KIS) Open API, Toss, Finnhub, FRED, Alpha Vantage, exchange-rate APIs, and Gemini for AI analysis. Data freshness and availability depend on those providers and on API keys.
- Usage follows a rhythm: check the market, analyze a stock, check the trade against personal rules, log the trade, then review monthly trade history and portfolio weights.
- The app is used on both desktop (sidebar navigation) and mobile web (bottom tab bar).

## Capabilities and Constraints

- Features: sign-up, login, and member info; friends and friend portfolio view; community; holdings, watchlist, and portfolio weighting; buy and sell logs with monthly history; investment rules and pre-trade rule check; stock price, financials, charts, and a composite score and grade; market indices, FX, news, and market diagnosis history; AI market analysis.
- Stack: Java 17, Spring Boot, Spring MVC, Thymeleaf server-side rendering with the layout dialect, JPA (H2 by default, MySQL optional), and Gradle. **Future UI work stays on Thymeleaf SSR with light JavaScript and no SPA framework.**
- **The UI is Korean only.** No i18n is required.
- **Korean market color convention:** red means up or gain and blue means down or loss. This must never be inverted.
- StockLog does not execute orders.

## Brand Commitments

- Name: StockLog.
- Existing UI copy is in Korean.

## Evidence on Hand

- Real data comes from the connected market APIs. Stock master lists are in `src/main/resources/data/`.
- There are no testimonials, user counts, performance claims, or press. Future work must not fabricate them.
- The README states that the analysis is not investment advice and that users are responsible for their own decisions. Keep this stance and never present scores or AI output as recommendations.

## Product Principles

1. **One coherent view.** Analysis, market context, and personal records should reinforce one another rather than sit in separate silos.
2. **Rules before impulse.** The flow toward logging a trade should bring the user's own rules into view before the trade is recorded.
3. **Show data honestly.** Make scores, grades, and AI output traceable to their inputs, and make data freshness and source gaps visible instead of hiding them.
4. **Serve a small, trusted circle.** Social features exist so friends can hold each other accountable, not to chase engagement or reach.
