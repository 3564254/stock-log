---
target: src/main/resources/templates/main/main.html
total_score: 19
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 3
target_identity: "file:C:\\JavaProjects\\stock-log\\src\\main\\resources\\templates\\main\\main.html"
target_fingerprint: "sha256:3e2a4bd3a57e0b466319bf157db74c5a9f538535a6be0f0a3933be6c474e7a24"
target_path: "C:\\JavaProjects\\stock-log\\src\\main\\resources\\templates\\main\\main.html"
timestamp: 2026-10-08T06-01-12Z
slug: src-main-resources-templates-main-main-html
closed: true
---
Method: dual-agent (A: design review sub-agent · B: detector sub-agent). Browser step skipped: bootRun failed (MySQL access denied for root@localhost).

## Design Health Score
| # | Heuristic | Score | Key Issue |
|---|---|---|---|
| 1 | Visibility of System Status | 2 | No data timestamp/source; failures only via alert() |
| 2 | Match System / Real World | 3 | Downtrend phases rendered red (= up in KR) |
| 3 | User Control and Freedom | 2 | Modal: no Esc, can't cancel analysis; no search clear |
| 4 | Consistency and Standards | 2 | Red overloaded; Bootstrap .btn/.badge/.btn-close leak; border cards vs DESIGN.md shadow |
| 5 | Error Prevention | 3 | Pre-trade rule check good; verdict shown with zero rules |
| 6 | Recognition Rather Than Recall | 2 | No price/target distance on rows; rules show pass/fail without values |
| 7 | Flexibility and Efficiency | 1 | No keyboard search nav, extra confirm click, no urgency sort |
| 8 | Aesthetic and Minimalist Design | 2 | ~40% of grid is "준비 중" placeholders |
| 9 | Error Recovery | 1 | alert(), no retry; failed search shows false "검색 결과 없음" |
| 10 | Help and Documentation | 1 | No phase/verdict explanation, no not-advice line |
| **Total** | | **19/40** | **Poor** |

## Design Specificity Verdict
Partly specific: generic Toss-style frame; per-row 검증 buttons are the product-true idea but under-staged (no Score Ring, no grade/price, rule check dead-ends at 닫기). Detector: 52 findings (main 41, sidebar 8, header 3): low-contrast Mist #8b95a1 ~3:1, white on #3182f6 3.7:1, logout hover 3.4:1; mobile tab labels 10.5px; h1→h4 skip on landing; off-scale radii/type/greys. False positives: cramped-padding on .search-dropdown (.p-2, x-show) and .rule-list; contrast counts inflated by both th:if branches.

## Priority Issues
- [P1] Rule-check modal frames AI as 승인/반려, hides measured values, verdict with zero rules, no timestamp, dead-ends at 닫기; alert() errors. Fix: factual "규칙 N개 중 M개 충족", value-vs-threshold rows, 기준 시각·출처, zero-rule CTA, primary 매매 기록하기, not-advice line, inline retry. → clarify, harden
- [P1] ~40% placeholders (L329-367) while market module has real data. Fix: market phase + indices/FX strip, or drop column. → distill, layout
- [P1] Accessibility: modal semantics/focus/Esc, unlabeled btn-close & search input, click-only div results, no focus-visible on .btn-verify/.ghost-btn, Mist contrast, 3.7:1 buttons, 10.5px tab labels, no aria-live. → audit, harden
- [P2] Red overloaded vs KR convention (bad phases, FAIL red; PASS = loss blue); 17 phase labels. → colorize
- [P2] Watch rows lack price/change/distance-to-target/grade; ready not sorted first; empty states no CTA; hero not actionable. → layout

## Persona Red Flags
Alex: debounce w/o Enter/arrows; extra analyze click, no cache; no log-trade from PASS. Sam: unnamed input, div results, modal no announce/focus/Esc, unlabeled close, no live region, heading skips, no focus rings. Minji: can't verify from search; PASS/FAIL beside "no rules"; alert() on failure; no friend signal.

## Minor Observations
0% renders red; substring on null korName (L288); dead sidebarOpen/.expanded; .hover-up opacity vs Blue Deep; mobile modal not bottom sheet; "계좌 평균"/"전략 평단가" ambiguous; duplicate login CTAs, no sign-up; off-scale radii/type.

## Questions
- Why a list of stocks, not decisions waiting on you?
- What does a PASS let you do next?
- Information or instruction: which does StockLog want to be accountable for?
