package com.stocklog.modules.stock.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocklog.modules.ai.AiProvider;
import com.stocklog.modules.stock.analysis.MarketRegimeService;
import com.stocklog.modules.stock.analysis.result.StockAnalysisResult;
import com.stocklog.modules.stock.briefing.CompanyBriefing;
import com.stocklog.modules.stock.briefing.CompanyBriefingService;
import com.stocklog.modules.stock.momentum.MomentumService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 종목 AI 리포트: 앱 분석 + 맥락을 AI에 넘겨 구조화된 리포트를 받는다 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StockAiReportService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AiProvider aiProvider;
    private final StockAiReportRepository repository;
    private final MomentumService momentumService;
    private final MarketRegimeService marketRegimeService;
    private final CompanyBriefingService briefingService;

    /** 화면용: 본문 + 만든 시각 + 오늘 것인지 */
    public record View(StockAiReport report, LocalDateTime createdAt, LocalDate reportDate, boolean today) {}

    public Optional<View> latest(String code) {
        return repository.findTopByCodeOrderByReportDateDesc(norm(code)).map(this::toView);
    }

    /** 종목당 하루(한국 날짜) 한 번: 오늘 리포트가 있으면 AI를 부르지 않고 그대로 돌려준다 */
    @Transactional
    public View generate(StockAnalysisResult analysis) {
        String code = norm(analysis.getCode());
        var existing = repository.findByCodeAndReportDate(code, LocalDate.now(KST));
        if (existing.isPresent()) return toView(existing.get());
        String raw = aiProvider.getStockReport(analysis, context(code));
        StockAiReport report = parse(raw);
        String json;
        try {
            json = MAPPER.writeValueAsString(report);
        } catch (Exception e) {
            json = "{\"raw\":\"\"}";
        }
        LocalDate today = LocalDate.now(KST);
        LocalDateTime now = LocalDateTime.now(KST);
        StockAiReportEntity e = repository.findByCodeAndReportDate(code, today).orElse(null);
        if (e == null) e = repository.save(new StockAiReportEntity(code, today, json, now));
        else e.replace(json, now);
        return new View(report, now, today, true);
    }

    /** AI에 같이 넘길 맥락 줄들. 실패한 항목은 빼고 계속한다 */
    private String context(String code) {
        List<String> lines = new ArrayList<>();
        boolean kr = code.matches("\\d+");
        if (!kr) {
            try {
                var mo = momentumService.view(code);
                if (mo != null && mo.ranked() && mo.pct() != null) {
                    lines.add(String.format("- 모멘텀(12-1개월, S&P500): 상위 %.0f%% · %s", mo.pct(),
                            mo.candidate() ? "분기 교체 기준 전략 대상 (상위 20%)" : "기준 전략 대상 아님"));
                } else if (mo != null && !mo.inUniverse()) {
                    lines.add("- 모멘텀: S&P500 밖이라 순위 없음");
                }
            } catch (Exception ex) { log.debug("[AI 리포트] 모멘텀 맥락 실패 {}", ex.getMessage()); }
        }
        try {
            var mr = marketRegimeService.forStock(code);
            if (mr != null && mr.known()) {
                lines.add(String.format("- 시장(%s): %s", mr.indexName(), mr.weak() ? "40주선 아래 (약세, 새로 살 때 위험을 줄이는 구간)" : "40주선 위"));
            }
        } catch (Exception ex) { log.debug("[AI 리포트] 시장 맥락 실패 {}", ex.getMessage()); }
        if (!kr) {
            try {
                CompanyBriefing.Earnings e = briefingService.nextEarnings(code);
                if (e != null) {
                    long d = ChronoUnit.DAYS.between(LocalDate.now(KST), e.date());
                    String when = switch (e.hour() == null ? "" : e.hour()) {
                        case "bmo" -> "장 시작 전"; case "amc" -> "장 마감 후"; case "dmh" -> "장중"; default -> "시간 미정";
                    };
                    lines.add(String.format("- 다음 실적 발표: %s %s (약 %d일 뒤)", e.date(), when, Math.max(0, d)));
                }
            } catch (Exception ex) { log.debug("[AI 리포트] 실적일 맥락 실패 {}", ex.getMessage()); }
            try {
                briefingService.latestSummary(code).ifPresent(s -> {
                    String c = s.getContent().replaceAll("\\s*\\[[0-9, ]+]", "");
                    if (c.length() > 700) c = c.substring(0, 700) + "…";
                    lines.add("- 최근 뉴스 AI 요약 (" + s.getSummaryDate() + "):\n" + c);
                });
            } catch (Exception ex) { log.debug("[AI 리포트] 뉴스 맥락 실패 {}", ex.getMessage()); }
        }
        return String.join("\n", lines);
    }

    /** ```json 울타리를 벗기고 해석. 결론 줄이 없으면 원문만 담는다 */
    private StockAiReport parse(String raw) {
        String t = raw == null ? "" : raw.strip();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        int a = t.indexOf('{'), b = t.lastIndexOf('}');
        if (a >= 0 && b > a) {
            try {
                // AI가 목록 칸을 문자열 하나로 주기도 해서 칸마다 느슨하게 읽는다
                JsonNode n = MAPPER.readTree(t.substring(a, b + 1));
                String headline = text(n.get("headline"));
                if (!headline.isBlank()) {
                    return new StockAiReport(headline, list(n.get("tags")), list(n.get("situation")),
                            scenario(n.get("up")), scenario(n.get("down")), list(n.get("actions")), list(n.get("risks")), null);
                }
            } catch (Exception e) {
                log.warn("[AI 리포트] JSON 해석 실패, 원문으로 보여 줌: {}", e.getMessage());
            }
        }
        return StockAiReport.rawOnly(t);
    }

    private static String text(JsonNode n) {
        return n == null || n.isNull() ? "" : (n.isTextual() ? n.asText() : n.toString()).strip();
    }

    /** 배열이면 그대로, 문자열 하나면 문장 단위로 나눠 목록으로 */
    private static List<String> list(JsonNode n) {
        List<String> out = new ArrayList<>();
        if (n == null || n.isNull()) return out;
        if (n.isArray()) {
            n.forEach(x -> { String s = text(x); if (!s.isBlank()) out.add(s); });
        } else {
            for (String s : text(n).split("(?<=[.!?요다])\\s+")) if (!s.isBlank()) out.add(s.strip());
        }
        return out;
    }

    private static StockAiReport.Scenario scenario(JsonNode n) {
        if (n == null || n.isNull()) return null;
        if (n.isTextual()) return new StockAiReport.Scenario(n.asText().strip(), "");
        String when = text(n.get("when")), then = text(n.get("then"));
        return when.isBlank() && then.isBlank() ? null : new StockAiReport.Scenario(when, then);
    }

    private View toView(StockAiReportEntity e) {
        StockAiReport r;
        try {
            r = MAPPER.readValue(e.getContent(), StockAiReport.class);
        } catch (Exception ex) {
            r = StockAiReport.rawOnly(e.getContent());
        }
        if ((r.headline() == null || r.headline().isBlank()) && r.raw() != null) r = parse(r.raw());
        return new View(r, e.getCreatedAt(), e.getReportDate(), e.getReportDate().equals(LocalDate.now(KST)));
    }

    private static List<String> nz(List<String> l) {
        return l == null ? List.of() : l.stream().filter(s -> s != null && !s.isBlank()).map(String::strip).toList();
    }

    private static String norm(String code) { return code == null ? "" : code.toUpperCase(); }
}
