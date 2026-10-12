---
name: StockLog
description: A clear, friendly window onto your trades, your rules, and the market.
colors:
  toss-blue: "#3182f6"
  toss-blue-deep: "#1b64da"
  toss-blue-press: "#1957c2"
  toss-blue-soft: "rgba(49,130,246,0.09)"
  toss-blue-tint: "#f0f6ff"
  toss-blue-tint-line: "#dbeafe"
  toss-red: "#f04452"
  toss-red-deep: "#cf2c3b"
  toss-red-soft: "rgba(240,68,82,0.09)"
  toss-red-tint: "#fff4f4"
  toss-red-tint-line: "#ffe5e5"
  status-green: "#05c072"
  status-green-text: "#04784a"
  status-green-soft: "rgba(5,192,114,0.10)"
  status-green-tint: "#eefaf4"
  status-green-line: "#c9eedc"
  caution-amber: "#f5a623"
  caution-text: "#9a5800"
  caution-tint: "#fff8ec"
  caution-line: "#fbe3b8"
  alert-text: "#7a4300"
  alert-tint: "#fdebd3"
  alert-line: "#f6d2a6"
  ink: "#191f28"
  ink-body: "#333d4b"
  slate: "#4e5968"
  slate-mid: "#6b7684"
  mist: "#8b95a1"
  label-text: "#626c7a"
  fog: "#b0b8c1"
  haze: "#d1d6db"
  hairline: "#e5e8eb"
  canvas: "#f2f4f6"
  canvas-hover: "#e8ebef"
  surface-soft: "#f9fafb"
  paper: "#ffffff"
typography:
  display:
    fontFamily: "Pretendard, sans-serif"
    fontSize: "48px"
    fontWeight: 800
    lineHeight: 1
    letterSpacing: "-2px"
    fontFeature: "\"tnum\""
  headline:
    fontFamily: "Pretendard, sans-serif"
    fontSize: "24px"
    fontWeight: 800
    lineHeight: 1.3
    letterSpacing: "-0.03em"
  title:
    fontFamily: "Pretendard, sans-serif"
    fontSize: "18px"
    fontWeight: 800
    lineHeight: 1.4
    letterSpacing: "-0.01em"
  body:
    fontFamily: "Pretendard, sans-serif"
    fontSize: "15px"
    fontWeight: 500
    lineHeight: 1.6
    letterSpacing: "-0.01em"
  body-sm:
    fontFamily: "Pretendard, sans-serif"
    fontSize: "14px"
    fontWeight: 500
    lineHeight: 1.6
    letterSpacing: "-0.01em"
  label:
    fontFamily: "Pretendard, sans-serif"
    fontSize: "13px"
    fontWeight: 600
    lineHeight: 1.4
    letterSpacing: "-0.01em"
  caption:
    fontFamily: "Pretendard, sans-serif"
    fontSize: "12px"
    fontWeight: 600
    lineHeight: 1.4
  figure:
    fontFamily: "Pretendard, sans-serif"
    fontSize: "20px"
    fontWeight: 800
    lineHeight: 1.2
    fontFeature: "\"tnum\""
rounded:
  xs: "4px"
  sm: "8px"
  md: "12px"
  field: "14px"
  lg: "16px"
  xl: "20px"
  pill: "999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  base: "16px"
  lg: "20px"
  xl: "24px"
  card: "28px"
  section: "32px"
components:
  button-primary:
    backgroundColor: "{colors.toss-blue-deep}"
    textColor: "{colors.paper}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "10px 24px"
  button-primary-hover:
    backgroundColor: "{colors.toss-blue-press}"
  button-tint:
    backgroundColor: "{colors.toss-blue-soft}"
    textColor: "{colors.toss-blue-deep}"
    typography: "{typography.label}"
    rounded: "{rounded.md}"
    padding: "9px 16px"
  button-tint-sell:
    backgroundColor: "{colors.toss-red-soft}"
    textColor: "{colors.toss-red-deep}"
    typography: "{typography.label}"
    rounded: "{rounded.md}"
    padding: "9px 16px"
  button-ghost:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.slate}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "8px 14px"
  card:
    backgroundColor: "{colors.paper}"
    rounded: "{rounded.xl}"
    padding: "{spacing.card}"
  data-cell:
    backgroundColor: "{colors.surface-soft}"
    rounded: "{rounded.md}"
    padding: "18px"
  input-search:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.field}"
    padding: "14px 20px"
  chip-status:
    backgroundColor: "{colors.toss-blue-soft}"
    textColor: "{colors.toss-blue-deep}"
    typography: "{typography.caption}"
    rounded: "{rounded.pill}"
    padding: "4px 10px"
  badge-profit:
    backgroundColor: "{colors.toss-red-soft}"
    textColor: "{colors.toss-red-deep}"
    rounded: "{rounded.sm}"
    padding: "5px 11px"
  badge-loss:
    backgroundColor: "{colors.toss-blue-soft}"
    textColor: "{colors.toss-blue-deep}"
    rounded: "{rounded.sm}"
    padding: "5px 11px"
  nav-item:
    textColor: "{colors.slate}"
    typography: "{typography.body}"
    rounded: "{rounded.md}"
    padding: "13px 20px"
  nav-item-active:
    backgroundColor: "{colors.toss-blue-tint}"
    textColor: "{colors.toss-blue-deep}"
  segmented-track:
    backgroundColor: "{colors.canvas}"
    rounded: "{rounded.md}"
    padding: "4px"
  segmented-tab-active:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    rounded: "{rounded.sm}"
    padding: "10px 6px"
---

# Design System: StockLog

## Overview

**Creative North Star: "The Clear Window"**

StockLog should feel like a clean pane of glass over your money. Nothing is hidden and nothing shouts, so every number can be read at a glance. The system comes from the Toss school of Korean fintech: a cool grey canvas, white surfaces lifted by soft shadows, one confident blue, and the Korean market's red–blue direction colors. It is friendly rather than technical, so a careful retail investor feels calm reading it and never feels pressured to trade.

Density is moderate. Screens use clear sections with generous internal padding, and figures are big, bold and tabular, with muted labels underneath. Color carries meaning instead of decoration: blue marks action and brand, red marks gain, blue also marks loss, and green or amber appear only as status signals. Everything else is a ladder of cool greys.

The system rejects three looks: the dense dark trading terminal (HTS or Bloomberg-style grids and neon tickers), crypto hype styling (gradients and glows everywhere, casino energy), and generic Bootstrap with its default components showing through. Bootstrap stays in the stack for grid and utilities only.

**Key Characteristics:**
- Cool grey canvas with white surfaces and soft ambient lift.
- One brand blue, plus red and blue under the Korean up/down convention.
- Pretendard throughout, with tight negative tracking and heavy weights for figures.
- Generous rounding (8–20px), tinted fills instead of outlines, and quick 0.15s state changes.
- Korean-only copy, with `word-break: keep-all` on prose.

## Colors

A cool, low-chroma grey ladder carries the structure, and two saturated signals, Toss Blue and Toss Red, carry meaning.

### Primary
- **Toss Blue** (`toss-blue`): the brand and action color, used for primary buttons, active navigation, focus rings, links and selected states. Under the Korean convention it also means **down / loss**. Text never uses it at full strength (3.4–3.7:1 on every surface): blue words, links, loss figures and the active nav label use **Toss Blue Deep** (`toss-blue-deep`, 4.8:1 or better on every surface), which is also the fill behind white button text. **Toss Blue Press** (`toss-blue-press`) is the pressed and hover fill. **Toss Blue Soft** (9% alpha) fills tinted buttons and badges. **Toss Blue Tint** with its tint-line border frames "premium" or informational panels and the active nav item.

### Secondary
- **Toss Red** (`toss-red`): **up / gain**, sell actions, and destructive actions (logout). Never use it for decoration. As with blue, red text uses **Toss Red Deep** (`toss-red-deep`, 4.6:1 or better on every surface) for gain figures, sell labels, errors and logout, while the full-strength red stays on bars, dots and chart marks. **Toss Red Soft** and **Toss Red Tint** mirror the blue family for gain badges and warning cards.

### Tertiary
- **Status Green** (`status-green`, text `status-green-text`): a positive *status* that has nothing to do with price, such as a market phase reading "good" or a rule passing. It always appears as a soft-fill pill, never as a price color.
- **Caution Amber** (`caution-amber`, text `caution-text` on `caution-tint`): warnings, unmet rules, overheated phases and drawdown flags. The raw amber is only for dots and icons; text always uses the darker `caution-text` (5.3:1).
- **Alert Amber** (`alert-text` on `alert-tint`): the severe end of the status scale (downtrend, stop-loss, danger phases). It always carries a leading 6px dot, so it reads apart from caution without relying on color.

### Neutral
- **Ink** (`ink`): headlines, figures and primary text.
- **Ink Body** (`ink-body`): default running text color on the home dashboard.
- **Slate** (`slate`): secondary text, idle nav items and ghost-button labels.
- **Slate Mid** (`slate-mid`): avatar fills and tertiary icons. Not for text on tinted or grey surfaces, where it drops below 4.5:1.
- **Label Text** (`label-text`): labels, captions, units, chart tick labels and muted meta. It is the lightest grey that still passes 4.5:1 on every surface, including the soft red and blue fills.
- **Mist** (`mist`): non-text only, such as icons, chevrons, dots, input icons and icon-only buttons (3:1 on Paper). Never use it for words.
- **Fog / Haze** (`fog`, `haze`): disabled icons, empty-state glyphs, scrollbar thumbs and dashed placeholder borders.
- **Hairline** (`hairline`): borders on inner cells, inputs, dropdowns and dividers.
- **Canvas** (`canvas`): the page background, the segmented-control track, ghost-button fill and row dividers. **Canvas Hover** (`canvas-hover`) is the hover fill for ghost buttons and grey controls.
- **Surface Soft** (`surface-soft`): inner data cells, insight boxes and hover backgrounds.
- **Paper** (`paper`): cards, modals, the sidebar and inputs.

### Named Rules
**The Direction Rule.** Red always means up or gain and blue always means down or loss, following the Korean market convention. Never invert them, and never use green for price movement.

**The Status-Not-Direction Rule.** Status (rule met / not met, phase health, drawdown) uses the scale green → grey → amber → deep amber with dot. Red and blue are never used for status, because in this market they mean price up and down. Counts that summarize status stay in Ink and get a small status dot.

**The Calm Signal Rule.** Saturated color appears only where it carries meaning: an action, a price direction or a status. Structure and chrome stay in the grey ladder.

**The Mark-and-Text Rule.** Every signal color has two strengths. The saturated value (`toss-blue`, `toss-red`, `status-green`, `caution-amber`) is for marks: fills without text, bars, dots, rings, chart lines and focus outlines. The deep value (`toss-blue-deep`, `toss-red-deep`, `status-green-text`, `caution-text`, `alert-text`) is for every word and number, and for any fill that carries white text. Grey follows the same split, with Mist for marks and Label Text for words.

**The Tint-Not-Outline Rule.** Secondary emphasis comes from a soft tinted fill (9–10% alpha of the signal color) with the full-strength color as text, not from colored outlines.

## Typography

**Display Font:** Pretendard (with sans-serif fallback)
**Body Font:** Pretendard
**Label/Mono Font:** Pretendard. Numbers use tabular figures (`font-variant-numeric: tabular-nums`) rather than a separate mono face.

**Character:** a single neo-grotesque Korean/Latin family. Heavy weights (700–800) and tight negative tracking make figures feel solid and calm, while 500–600 weights keep labels quiet.

### Hierarchy
- **Display**: the composite score inside the score ring, and nowhere else.
- **Headline**: the page greeting or title (21px on mobile).
- **Title**: section and card titles. Use 15px/700 for sub-section headers inside a card.
- **Body**: stock names (at 700), list rows and report prose. Report prose uses line-height 1.7 with `keep-all`.
- **Body-sm**: secondary copy, dropdown items and insight boxes.
- **Label**: buttons, meta lines and cell labels.
- **Caption**: chips, badges, stat-chip keys and nav group labels.
- **Figure**: stat values and data-cell values, with the unit in Label Text at 13px/600 next to it.

### Named Rules
**The Heavy Number Rule.** Figures are the loudest text on any card, at 700–800 weight and Ink (or a direction color) with tabular figures. Their labels sit beside or below them in Label Text at 600 weight.

**The One Family Rule.** Pretendard only. The unused `community.html` still loads Noto Sans KR, which is drift and should not spread.

## Layout

The desktop shell is a fixed 250px white sidebar on the left (collapsible to 80px) with a grey canvas to its right. The content column is centered with a maximum width of 1100–1200px and page padding of `0 60px 80px`. A header row (the profile pill on the right) sits above the content.

Pages stack full-width cards vertically with a 24px gap. Inside a card, Bootstrap's grid lays out data cells in 2–4 columns, and lists separate their rows with a 1px Canvas divider at 14–16px row padding.

The spacing rhythm is 4 / 8 / 12 / 16 / 20 / 24, with 28–32px card padding. Gaps between inline elements sit at 8–14px.

**Responsive:** at ≤768px the sidebar is hidden and replaced by a fixed bottom tab bar (white with a Hairline top border, 11px labels (the legibility floor) and the active tab in Toss Blue Deep, respecting `safe-area-inset-bottom`). Page padding drops to 16px, card padding to 20px, stat chips stretch to fill the row, and list rows may wrap. Secondary breakpoints at 992px and 1100px reflow multi-column grids.

## Elevation & Depth

Depth comes from **soft ambient shadows on white surfaces over the grey canvas**. Cards float gently instead of being boxed in by borders. Inner cells use tonal layering (a Surface Soft fill with a Hairline border) rather than more shadow. Shadow strength increases only for layers that truly float above the page.

### Shadow Vocabulary
- **Card ambient** (`box-shadow: 0 4px 20px rgba(0,0,0,0.03)`): every top-level card and the sidebar's content cards.
- **Raised control** (`box-shadow: 0 1px 4px rgba(0,0,0,0.05)`): the active segment in a segmented tab control.
- **Dropdown** (`box-shadow: 0 12px 32px rgba(0,0,0,0.08)`): search results and the profile menu.
- **Modal** (`box-shadow: 0 20px 50px rgba(0,0,0,0.15)`): modal sheets, over an Ink scrim at 50% with a 4px backdrop blur.
- **Focus halo** (`box-shadow: 0 0 0 4px rgba(49,130,246,0.10)` with a Toss Blue border): focused search and input fields.

### Named Rules
**The Whisper Lift Rule.** Card shadows stay at or below 0.03–0.04 alpha, so a card should look lifted rather than shadowed. Anything heavier is reserved for dropdowns and modals.

**The No Double Edge Rule.** A top-level card gets an ambient shadow *or* a Hairline border, never both. Several current pages (home, analysis) still use the border-only version, which is legacy to migrate toward the shadow.

## Shapes

The form language is soft and friendly: generously rounded rectangles that never go fully pill-shaped except on chips and the profile pill.

- **XL (20px):** top-level cards and modal sheets (24px on the legacy sheets).
- **LG (16px):** list items, entry boxes and chart boxes.
- **Field (14px):** search boxes, stat chips, stock avatars and grouped rule lists.
- **MD (12px):** data cells, nav items, the segmented track, tinted buttons and banners.
- **SM (8px):** primary and ghost buttons, profit/loss badges, segment tabs and dropdown items.
- **Pill (999px):** status chips, count pills and the header profile pill.
- **Dots:** 6–8px circles mark status (a dot before phase-badge text, axis dots and signal dots). Section headers may use a 4×16px rounded color bar.

Bottom-sheet modals on mobile round only their top corners (`24px 24px 0 0`).

## Components

### Buttons
Buttons are soft, tinted and quick to respond.
- **Shape:** gently rounded (8px for solid and ghost buttons, 12px for tinted ones).
- **Primary:** Toss Blue Deep fill (5.4:1 with white) with white 14px/700 text and `10px 24px` padding, used for the single main action in a view (log in, save).
- **Hover / Focus:** the fill deepens to Toss Blue Press over 0.15s. Focus shows a 2px Toss Blue outline offset by 2px.
- **Tint:** a Toss Blue Soft fill with Toss Blue Deep text for in-row actions such as 매수 검증. The sell variant swaps in red. On hover the fill alpha rises to about 16%.
- **Ghost:** a Canvas fill with Slate text for secondary navigation actions (more, view all). On hover it moves to `#e8ebef` with Ink text.

### Chips
- **Status chip:** a pill-shaped soft-tone fill with a 6px dot in `currentColor` before the 12px/700 label. It comes in five tones: info (blue tint with Toss Blue Deep), good (green), caution (amber), alert (deep amber) and neutral (Canvas with Slate). Price direction is never a status tone, so a "bad" status uses caution or alert, not red.
- **Count pill:** a small Toss Blue Soft pill next to section titles.
- **Meta pill:** Canvas with Slate text for target prices and similar secondary facts.

### Cards / Containers
- **Corner Style:** 20px.
- **Background:** Paper on the Canvas page.
- **Shadow Strategy:** card ambient (see Elevation), with no border.
- **Internal Padding:** 28–32px, dropping to 20px on mobile.
- **Section head:** a Title on the left with an optional Label Text subline, and a ghost action on the right.
- **Inner data cell:** a Surface Soft fill with a Hairline border at 12px radius and 18px padding, holding a Label Text label above a Figure. Blue- and red-accent variants switch to the tint fill, the tint-line border and the deep text color.

### Inputs / Fields
- **Style:** a Paper fill with a Hairline border, 14px radius, `14px 20px` padding and a leading Mist search icon.
- **Focus:** the border turns Toss Blue and gains a 4px blue halo at 10% opacity.
- **Legacy variant:** a borderless Canvas fill that turns into a blue tint with a 2px ring on focus (old `custom.css`). Prefer the bordered Paper field.

### Navigation
- **Sidebar:** Paper with a Hairline right edge. The logo is a 32px blue circle mark next to "StockLog" in 20px/800.
- **Nav item:** 15px/600 Slate text with an 18px Bootstrap icon, 12px radius and `13px 20px` padding. Hover shows a Canvas fill with Ink text. Active shows a Toss Blue Tint fill, blue 700 text and a filled icon variant, plus `aria-current="page"`.
- **Group labels:** 12px/700 Label Text.
- **Mobile:** a bottom tab bar replaces the sidebar (see Layout).
- **Header:** the profile pill (Paper, Hairline border, pill-shaped, with a 32px avatar) opens a 12px-radius dropdown whose 8px-radius items turn Canvas on hover. Logout is red.

### Segmented Tabs
A Canvas track with 12px radius and 4px padding. Each tab is an equal flex item in 14px/600 Label Text, and the active tab becomes Paper and Ink with the raised-control shadow.

### Score Ring (signature)
The stock-analysis hero is a 180px ring chart with the Display score in its center, a Label Text unit below it and the grade in 15px/700. Next to it is a list of axis rows (valuation, momentum, technical, and so on). Each row has an 8px colored dot, a 14px/700 name over a 12px Label Text subline, a 6px pill-shaped bar on a Canvas track (its width animates over 1s), and a right-aligned 15px/700 score over a Label Text max value. This is what makes StockLog's analysis feel unified, so reuse it wherever a composite score appears.

### Stat Chip
A Paper chip with a Hairline border at 14px radius, padding `12px 18px` and a minimum width of 112px. It holds a 12px Label Text key above a 20px/800 value. The **hot** variant adds a faint blue border and a white-to-`#f5f9ff` vertical wash, with the value in blue.

## Do's and Don'ts

### Do:
- **Do** use red (`#f04452`) for up, gain and sell, and blue (`#3182f6`) for down and loss, in every number, badge and chart.
- **Do** emphasize with a soft tint fill (9–10% alpha) and full-strength text, as in the profit/loss badges and status chips.
- **Do** set every figure in Pretendard at 700–800 with `tabular-nums`, with its unit in Label Text at 13px/600.
- **Do** lift top-level cards with `0 4px 20px rgba(0,0,0,0.03)` on Paper over the Canvas page, at 20px radius.
- **Do** keep state transitions to about 0.15s on background, color and border.
- **Do** apply `word-break: keep-all` to Korean prose and labels.
- **Do** replace the sidebar with the bottom tab bar at ≤768px.

### Don't:
- **Don't** use green or a green/red pair for price movement. Green is only a status tone.
- **Don't** build a dark, dense trading-terminal look, with neon tickers or tightly packed grids.
- **Don't** use crypto or hype styling, such as gradients on surfaces, glows or flashing color. The only allowed wash is the faint `#fff → #f5f9ff` on a highlighted stat chip or the landing hero.
- **Don't** let default Bootstrap buttons, cards, tables or `.btn-primary` blue show through. Bootstrap is only for grid and utilities.
- **Don't** give one card both a shadow and a border, or a card shadow above 0.04 alpha.
- **Don't** introduce a second typeface. The unused `community.html` still loads Noto Sans KR, which is drift.
- **Don't** put Toss Blue as an action color right beside a blue loss figure without a label. Pair them with text so brand blue and down blue can't be confused.

## Implementation

The system lives in three shared files, and every page loads them through one fragment.

- **`static/css/tokens.css`**: every value in this document's frontmatter as a CSS variable, named exactly as the token (`--toss-blue-deep`, `--label-text`, `--radius-md`, `--shadow-modal`). It also holds the layout variables pages override (`--content-max`, `--content-top`, `--page-gutter`, `--card-pad`, `--card-radius`, `--sheet-max`, plus `-m` variants for 768px and below). Change a color here and in this file together.
- **`static/css/base.css`**: the shared shell and base elements. That covers `body`, the `:focus-visible` ring, `.layout-wrapper`/`.main-content`/`.content-container`, `.toss-card`, `.toss-input`, `.btn-primary-toss`, `.modal-backdrop-custom`/`.modal-sheet`, and the 768px collapse to the bottom tab bar.
- **`templates/fragments/head.html`** (`assets`): Pretendard, Bootstrap CSS, Bootstrap Icons, both stylesheets and `static/js/ui-a11y.js`, in that order. Place it right after `<title>` and before the page `<style>`. Scripts only some pages need (Alpine, Chart.js, lightweight-charts, the Bootstrap JS bundle) stay in those pages.

**Page rules.** A page's `<style>` starts with a `:root` block that only sets layout variables and local aliases pointing at tokens (`--line: var(--hairline)`), never raw hex. Page CSS uses `var(--token)` for every palette value. One-off tints that are not tokens stay in the page; promote a value to `tokens.css` once three places use it with the same intent. Canvas-drawn chart colors in JavaScript cannot read CSS variables, so they keep literal hex that must match the tokens.

**Accessible behaviour** comes from `static/js/ui-a11y.js`. Use `x-dialog="openState"` on a modal sheet with `role="dialog" aria-modal="true" aria-labelledby tabindex="-1"` for focus entry, trap, scroll lock and focus return, and `x-arrow-nav` with `data-nav-item` and `data-nav-input` for ↑/↓ movement through search results.

