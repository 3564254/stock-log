package com.stocklog.modules.purchase.trade.service;

import com.stocklog.modules.stock.analysis.signal.trading.TradingSignalResult;
import org.springframework.web.util.HtmlUtils;

/**
 * 타점 엔진 결과(Narrative)를 검증 모달의 "종합 분석 리포트" HTML로 바꾼다.
 * 내용은 전부 escape하고, 화면(portfolio.html)이 허용 태그/스타일만 남기도록 한 번 더 정리한다.
 */
final class TradeReportFormatter {

    private TradeReportFormatter() {}

    static String buyReport(TradingSignalResult signal) {
        TradingSignalResult.Narrative n = signal == null ? null : signal.narrative();
        if (n == null) return "";

        StringBuilder sb = new StringBuilder();
        if (notBlank(n.headline())) {
            sb.append("<div style=\"font-weight:800;font-size:15px;color:#191f28;\">").append(esc(n.headline())).append("</div>");
        }
        if (notBlank(n.summary())) {
            sb.append("<div style=\"margin-top:4px;\">").append(esc(n.summary())).append("</div>");
        }

        if (n.details() != null && !n.details().isEmpty()) {
            sb.append("<ul style=\"margin:12px 0 0;padding-left:18px;\">");
            for (TradingSignalResult.Narrative.Detail d : n.details()) {
                sb.append("<li style=\"margin-top:4px;\">");
                String head = ((notBlank(d.icon()) ? d.icon() + " " : "") + (notBlank(d.label()) ? d.label() : "")).strip();
                if (!head.isEmpty()) sb.append("<b style=\"color:#191f28;\">").append(esc(head)).append("</b> ");
                sb.append(esc(d.text())).append("</li>");
            }
            sb.append("</ul>");
        }

        if (notBlank(n.actionTitle()) || notBlank(n.actionBody())) {
            sb.append("<div style=\"margin-top:14px;padding-top:14px;border-top:1px solid #e5e8eb;\">");
            if (notBlank(n.actionTitle())) {
                sb.append("<div style=\"font-weight:800;color:").append(toneColor(n.tone())).append(";\">")
                  .append(esc(n.actionTitle())).append("</div>");
            }
            if (notBlank(n.actionBody())) {
                sb.append("<div style=\"margin-top:4px;\">").append(esc(n.actionBody())).append("</div>");
            }
            sb.append("</div>");
        }
        return sb.toString();
    }

    /** analysis.html과 같은 규칙: bull=빨강(상승), bear=파랑, warn=주황. */
    private static String toneColor(String tone) {
        if (tone == null) return "#191f28";
        return switch (tone) {
            case "bull" -> "#f04452";
            case "bear" -> "#3182f6";
            case "warn" -> "#d48806";
            default -> "#191f28";
        };
    }

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s == null ? "" : s);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
