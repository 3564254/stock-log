---
target: src/main/resources/templates/portfolio/portfolio.html
total_score: 21
max_score: 40
na_heuristics: 
p0_count: 1
p1_count: 2
target_identity: "file:C:\\JavaProjects\\stock-log\\src\\main\\resources\\templates\\portfolio\\portfolio.html"
target_fingerprint: "sha256:8d30b6c3861cd25d45a59857d5fd80efcc76efa38ac6fa24ee1635872683d7e2"
target_path: "C:\\JavaProjects\\stock-log\\src\\main\\resources\\templates\\portfolio\\portfolio.html"
timestamp: 2026-10-08T06-20-48Z
slug: ources-templates-portfolio-portfolio-html-713b9f23
closed: true
---
Method: dual-agent (A: design review sub-agent · B: detector sub-agent). Browser skipped: app can't boot (MySQL access denied for root@localhost).

## Design Health Score
| # | Heuristic | Score | Key Issue |
|---|---|---|---|
| 1 | Visibility of System Status | 2 | Timer-faked loading steps; in-memory undated stamps; no price as-of |
| 2 | Match System / Real World | 3 | "AI 검증" mislabels a rule+chart engine; VCP/이격도/불트랩 unexplained |
| 3 | User Control and Freedom | 2 | Native confirm(), no undo; rules can't be edited |
| 4 | Consistency and Standards | 1 | Red/blue used for status; vocabulary differs from home; 13 labels; Bootstrap leaks |
| 5 | Error Prevention | 2 | Zero rules => 진입 승인; delete prompts don't name loss |
| 6 | Recognition Rather Than Recall | 3 | Rule sentences excellent; failed rule shows no measured value |
| 7 | Flexibility and Efficiency | 2 | No verify-all/bulk; edit rule = delete+recreate+reload |
| 8 | Aesthetic and Minimalist Design | 2 | 4 tiles + 3 equal cards + dense rows; no lead region |
| 9 | Error Recovery | 2 | Generic errors; rule-form errors as toasts |
| 10 | Help and Documentation | 2 | Nothing explains how 승인/보류 is decided |
| **Total** | | **21/40** | **Acceptable** |

## Design Specificity Verdict
Specific content (17-phase vocabulary, drawdown from high, KRW/USD, rule sentences + presets), generic Toss-list frame; "rules before impulse" not expressed in composition. Detector: 37 findings — low-contrast 22 (--text-mute #8b95a1 3.0/2.9/2.8:1; white on #3182f6 3.7:1), layout-transition 1 (.pass-bar width), advisories 14. False positives: icon font-sizes, modal scrim color, hidden modals/empty states inflating counts.

## Priority Issues
- [P0] Verify result misstates data: zero rules => "진입 승인 · 설정한 규칙을 모두 충족했어요" (L781); "차트 분석만으로 판단" claim (L443/672) while signalVerdict is never read; sell stub always "보유 유지" and stamps the row (L309). → clarify
- [P1] Red/blue as status (rule pass/fail L662, pass bar, verdict heroes, phases, tile counts L261/266, red drawdown beside blue loss L300). → colorize
- [P1] Modal diverges from home (5 names for one action, pre-step, no analysis time, no record CTA, dead detail.actualValue L659). → clarify, polish
- [P2] Keyboard/SR: div-only indicator picker L534/583 and search results L475; no dialog semantics; unnamed switch L431; silent toasts; unassociated labels; mute contrast. → audit, harden
- [P2] DESIGN.md drift: border cards 16px, modal shadow/scrim, Bootstrap btn-close/btn-link/spinner/text-danger, mixed title sizes, no mobile bottom sheet. → polish

## Persona Red Flags
Alex: no verify-all, undated volatile stamps, rule edit = recreate + reload. Sam: cannot add rule or stock by keyboard; unnamed switch; silent results; focus escapes modal. Minji: zero-rule "진입 승인" read as approval; sell check always "보유 유지".

## Minor Observations
EQUAL_TO "일 때 일 때"; imperative phase descriptions; unanalyzed items rank 10 & hidden by filter; 0% win bar 50/50; unweighted 평균 수익률; isKrCode misses alphanumeric KRX codes; width transition; 3 card-title sizes.

## Questions
- Why are rules a side column instead of the frame of every row?
- Should a zero-rule verify be allowed at all?
- No sell check vs. one that always says 홀딩?
