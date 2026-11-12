package com.stocklog.modules.purchase.portfolio.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocklog.modules.ai.AiException;
import com.stocklog.modules.ai.AiProvider;
import com.stocklog.modules.stock.analysis.MarketRegimeService;
import com.stocklog.modules.stock.briefing.CompanyBriefing;
import com.stocklog.modules.stock.briefing.CompanyBriefingService;
import com.stocklog.modules.stock.momentum.MomentumService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 포트폴리오 AI 주간 점검. 화면이 계산한 규칙 결과(금액 없음) + 서버 맥락을 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PortfolioAiReviewService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_HOLDINGS = 30;

    private final AiProvider aiProvider;
    private final PortfolioAiReviewRepository repository;
    private final MomentumService momentumService;
    private final MarketRegimeService marketRegimeService;
    private final CompanyBriefingService briefingService;

    /** 화면용: 본문 + 만든 시각 + 그 주 시작일 + 이번 주 것인지 + 다음에 받을 수 있는 날 */
    public record View(PortfolioAiReview review, LocalDateTime createdAt, LocalDate weekStart, boolean thisWeek, LocalDate nextAvailable) {}

    public static LocalDate weekStart(LocalDate d) { return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }

    public Optional<View> latest(Long memberId) {
        return repository.findTopByMemberIdOrderByWeekStartDesc(memberId).map(this::toView);
    }

    @Transactional
    public View generate(Long memberId, ReviewFacts facts) {
        LocalDate week = weekStart(LocalDate.now(KST));
        var existing = repository.findByMemberIdAndWeekStart(memberId, week);
        if (existing.isPresent()) return toView(existing.get());
        if (facts == null || facts.holdings() == null || facts.holdings().isEmpty()) {
            throw new AiException(AiException.Kind.BLOCKED, "no holdings");
        }
        String factText = describe(facts);
        String previous = repository.findTopByMemberIdAndWeekStartBeforeOrderByWeekStartDesc(memberId, week)
                .map(e -> "점검일 " + e.getCreatedAt().toLocalDate() + "\n" + e.getFacts())
                .orElse("");
        String raw = aiProvider.getPortfolioReview(factText, context(facts), previous);
        PortfolioAiReview review = parse(raw);
        String json;
        try { json = MAPPER.writeValueAsString(review); } catch (Exception e) { json = "{\"raw\":\"\"}"; }
        LocalDateTime now = LocalDateTime.now(KST);
        repository.save(new PortfolioAiReviewEntity(memberId, week, json, factText, now));
        return new View(review, now, week, true, week.plusWeeks(1));
    }

    /** 화면 사실을 AI용 글로 */
    private String describe(ReviewFacts f) {
        List<String> out = new ArrayList<>();
        out.add("성향: " + safe(f.profile(), 10));
        out.add("점검 결론: " + safe(f.headline(), 80));
        if (f.todo() != null && !f.todo().isEmpty()) {
            out.add("지금 할 일(앱 규칙):");
            f.todo().stream().limit(4).forEach(t -> out.add("- " + safe(t, 160)));
        }
        if (f.checks() != null && !f.checks().isEmpty()) {
            out.add("점검 항목:");
            f.checks().stream().limit(12).forEach(t -> out.add("- " + safe(t, 120)));
        }
        out.add("보유 종목 (판정은 앱 규칙):");
        f.holdings().stream().limit(MAX_HOLDINGS).forEach(h -> out.add(String.format("- %s(%s) · %s · 수익률 %s · 계좌 비중 %s · 기준선까지 %s%s%s",
                safe(h.name(), 30), safe(h.code(), 12), stateLabel(h.state(), Boolean.TRUE.equals(h.profitLine())),
                pct(h.returnPct(), true), pct(h.weightPct(), false), pct(h.lineGapPct(), false),
                Boolean.TRUE.equals(h.longDown()) ? " · 40주선 아래" : "",
                Boolean.TRUE.equals(h.indexEtf()) ? " · 지수 ETF" : "")));
        return String.join("\n", out);
    }

    private static String stateLabel(String s, boolean profitLine) {
        if (s == null) return "기준선 없음";
        return switch (s) {
            case "ok" -> "유지";
            case "targetNear" -> "유지 · 목표 근처";
            case "lossNear" -> "주의 · 손절선 3% 이내";
            case "intraBelow" -> "주의 · 장중 기준선 아래 (종가 기준 판정)";
            case "drawdown" -> "주의 · 고점 대비 -10% 이상";
            case "lossBroken" -> "매도 · 손절선 이탈";
            case "profitBroken" -> "매도 · " + (profitLine ? "수익 보호선 이탈" : "기준선 이탈");
            case "momHold" -> "유지 · 모멘텀 전략(상위 20% 안, 손절선 없음)";
            case "momOut" -> "주의 · 모멘텀 전략(지금 상위 20% 밖, 교체일까지 보유)";
            case "momExit" -> "매도 · 모멘텀 전략(분기 교체에서 상위 20% 밖)";
            default -> "기준선 없음";
        };
    }

    private String context(ReviewFacts f) {
        List<String> lines = new ArrayList<>();
        LocalDate today = LocalDate.now(KST);
        String nextRebal = null;
        boolean anyKr = false, anyUs = false;
        for (ReviewFacts.HoldingFact h : f.holdings().stream().limit(MAX_HOLDINGS).toList()) {
            String code = h.code() == null ? "" : h.code().toUpperCase();
            if (code.isBlank()) continue;
            if (code.matches("\\d+")) { anyKr = true; continue; }
            anyUs = true;
            try {
                CompanyBriefing.Earnings e = briefingService.nextEarnings(code);
                if (e != null) {
                    long d = ChronoUnit.DAYS.between(today, e.date());
                    if (d >= 0 && d <= 14) lines.add(String.format("- %s 실적 발표 %s (%d일 뒤)", safe(h.name(), 30), e.date(), d));
                }
            } catch (Exception ex) { log.debug("[주간 점검] 실적일 실패 {} {}", code, ex.getMessage()); }
            try {
                var mo = momentumService.view(code);
                if (mo != null && mo.ranked() && mo.pct() != null) {
                    lines.add(String.format("- %s 모멘텀 상위 %.0f%%%s", safe(h.name(), 30), mo.pct(), mo.candidate() ? " (분기 교체 전략 대상)" : " (전략 대상 아님)"));
                }
                if (mo != null && mo.nextRebalance() != null) nextRebal = mo.nextRebalance();
            } catch (Exception ex) { log.debug("[주간 점검] 모멘텀 실패 {} {}", code, ex.getMessage()); }
        }
        for (Map.Entry<String, Boolean> m : Map.of("005930", anyKr, "SPY", anyUs).entrySet()) {
            if (!m.getValue()) continue;
            try {
                var mr = marketRegimeService.forStock(m.getKey());
                if (mr != null && mr.known()) lines.add(String.format("- 시장(%s): %s", mr.indexName(), mr.weak() ? "40주선 아래 (약세)" : "40주선 위"));
            } catch (Exception ex) { log.debug("[주간 점검] 시장 실패 {}", ex.getMessage()); }
        }
        if (nextRebal != null) lines.add("- 다음 모멘텀 분기 교체일: " + nextRebal);
        lines.add("- 오늘: " + today);
        return String.join("\n", lines);
    }

    private PortfolioAiReview parse(String raw) {
        String t = raw == null ? "" : raw.strip();
        if (t.startsWith("```")) t = t.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        int a = t.indexOf('{'), b = t.lastIndexOf('}');
        if (a >= 0 && b > a) {
            try {
                JsonNode n = MAPPER.readTree(t.substring(a, b + 1));
                String headline = text(n.get("headline"));
                if (!headline.isBlank()) {
                    List<PortfolioAiReview.Watch> watch = new ArrayList<>();
                    JsonNode w = n.get("watch");
                    if (w != null && w.isArray()) w.forEach(x -> {
                        if (x.isTextual()) { if (!x.asText().isBlank()) watch.add(new PortfolioAiReview.Watch("", x.asText().strip())); }
                        else { String nm = text(x.get("name")), why = text(x.get("why")); if (!nm.isBlank() || !why.isBlank()) watch.add(new PortfolioAiReview.Watch(nm, why)); }
                    });
                    return new PortfolioAiReview(headline, list(n.get("tags")), list(n.get("changes")), list(n.get("todo")),
                            watch, list(n.get("schedule")), null);
                }
            } catch (Exception e) {
                log.warn("[주간 점검] JSON 해석 실패, 원문으로 보여 줌: {}", e.getMessage());
            }
        }
        return PortfolioAiReview.rawOnly(t);
    }

    private static String text(JsonNode n) {
        return n == null || n.isNull() ? "" : (n.isTextual() ? n.asText() : n.toString()).strip();
    }

    private static List<String> list(JsonNode n) {
        List<String> out = new ArrayList<>();
        if (n == null || n.isNull()) return out;
        if (n.isArray()) n.forEach(x -> { String s = text(x); if (!s.isBlank()) out.add(s); });
        else for (String s : text(n).split("(?<=[.!?요다])\\s+")) if (!s.isBlank()) out.add(s.strip());
        return out;
    }

    private View toView(PortfolioAiReviewEntity e) {
        PortfolioAiReview r;
        try { r = MAPPER.readValue(e.getContent(), PortfolioAiReview.class); }
        catch (Exception ex) { r = PortfolioAiReview.rawOnly(e.getContent()); }
        if ((r.headline() == null || r.headline().isBlank()) && r.raw() != null) r = parse(r.raw());
        LocalDate week = weekStart(LocalDate.now(KST));
        return new View(r, e.getCreatedAt(), e.getWeekStart(), e.getWeekStart().equals(week), week.plusWeeks(e.getWeekStart().equals(week) ? 1 : 0));
    }

    private static String pct(Double v, boolean signed) {
        if (v == null || v.isNaN() || v.isInfinite()) return "-";
        return signed ? String.format("%+.1f%%", v) : String.format("%.1f%%", v);
    }

    /** 길이 제한 + 금액 표기 제거 + 줄바꿈 제거 */
    private static String safe(String s, int max) {
        if (s == null) return "";
        String t = s.replaceAll("[\\r\\n]+", " ")
                .replaceAll("[-+]?\\$\\s?[\\d,]+(\\.\\d+)?", "(금액)")
                .replaceAll("[-+]?[\\d,]+(\\.\\d+)?\\s?(만|억)?원", "(금액)")
                .strip();
        return t.length() > max ? t.substring(0, max) + "…" : t;
    }
}
