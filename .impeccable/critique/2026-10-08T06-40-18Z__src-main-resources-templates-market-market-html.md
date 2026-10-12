---
target: "src/main/resources/templates/main/market.html (resolved: market/market.html)"
total_score: 13
max_score: 40
na_heuristics: 
p0_count: 2
p1_count: 3
target_identity: "file:C:\\JavaProjects\\stock-log\\src\\main\\resources\\templates\\market\\market.html"
target_fingerprint: "sha256:8f7e2514894c696f42b2dffad7a344498ebaa6c3f53a171217363ed533421407"
target_path: "C:\\JavaProjects\\stock-log\\src\\main\\resources\\templates\\market\\market.html"
timestamp: 2026-10-08T06-40-18Z
slug: src-main-resources-templates-market-market-html
closed: true
---
Method: dual-agent (A: design review sub-agent · B: detector sub-agent). Target resolved from main/market.html (nonexistent) to market/market.html (served at /market). Browser skipped: app can't boot (MySQL access denied).

## Design Health Score
| # | Heuristic | Score | Key Issue |
|---|---|---|---|
| 1 | Visibility of System Status | 1 | No as-of on diagnosis (diag.date unused), no source, no market open/closed |
| 2 | Match System / Real World | 1 | Raw enums as headlines; CPI shown as %; 실질 GDP is nominal |
| 3 | User Control and Freedom | 2 | No chart range; tab not in URL |
| 4 | Consistency and Standards | 1 | ~6 places status in red/blue; cards border+16px |
| 5 | Error Prevention | 1 | RISK_ON vs 'RISK-ON' mismatch -> wrong risk badge & compass copy; empty series keeps old line |
| 6 | Recognition Rather Than Recall | 2 | No scale legend; confidence only in title |
| 7 | Flexibility and Efficiency | 2 | Row->chart shortcut mouse-only; no presets |
| 8 | Aesthetic and Minimalist Design | 2 | Two heroes + narrative + chart + 9 panels/40 rows flat |
| 9 | Error Recovery | 0 | One service failure kills page; null analysis vanishes; AI error string rendered |
| 10 | Help and Documentation | 1 | No disclaimer; no score/confidence explanation |
| **Total** | | **13/40** | **Poor** |

## Design Specificity Verdict
Toss surface over a US-only FRED macro terminal; no KOSPI/KOSDAQ/USD-KRW; "AI 퀀트 진단" is rules. Detector: 25 findings — low-contrast 8 (#8b95a1 3.0:1 ×5; #05c072 on #e2f9ec 2.2:1; #f5a623 1.9:1 ×2), design-system-color 4, font-size 5, cramped-padding 4 (likely FP), layout-transition 2, broken-image 1 (FP, th:src), radius 1.

## Priority Issues
- [P0] Diagnosis copy/colors untrue: RISK_ON mismatch (L589/595/617), raw enums, status in red/blue (L49-50, L200-250, L267-302, L563, L766), blue momentum bar when positive. → colorize, clarify
- [P0] All-or-nothing/silent failure: MarketController no isolation; null analysis hidden; fallback "0.00 (0.00%)"; stale chart on empty series (L816); Gemini error as narrative; dead empty-news hover. → harden
- [P1] Freshness/source/units invisible; CPI %, GDP label, DTWEXBGS label. → clarify
- [P1] AI framing as advice; confidence = |score|. → clarify, quieter
- [P1] Keyboard/SR: div rows, no tab ARIA, silent chart/gauges, contrast, reduced motion. → audit, harden

## Persona Red Flags
Alex: no range/compare, stale chart, unitless, stuck risk badge. Sam: rows unreachable, tabs unannounced, chart silent. Min-ji (08:50 mobile): no KR indices/FX, no as-of, enum card first, 11px grey dates, sync Gemini on first load, placehold.co thumbnails.

## Minor Observations
chartInitialzed typo; duplicated type lists; getConfidenceColor defaults bull/red; no chart autoSize; 불러오는 중 on SSR; sparkline color by last change; 합니다체 empty state; mixed-language label; uncapped pagination; market_improved.html dead.

## Questions
- One question at 08:50: 6 tickers + one sentence + a date?
- What does "AI 수석 전략가" add besides authority?
- Should a regime ever be red?
