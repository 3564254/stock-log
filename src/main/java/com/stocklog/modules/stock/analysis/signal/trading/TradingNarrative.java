package com.stocklog.modules.stock.analysis.signal.trading;

import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.common.type.ChartPhase;
import com.stocklog.modules.stock.analysis.signal.trading.TradingSignalResult.Narrative;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 국면(Phase)별 상황 설명과 대응 안내 문구를 만든다.
 * - 진입 계획이 없어도(NO_SETUP) "지금 시장이 어떤 상태이고 무엇을 지켜봐야 하는지"를 항상 설명한다.
 * - HTML을 만들지 않고 순수 텍스트 구조(Narrative)만 반환한다. 렌더링은 템플릿 몫이다.
 * - 지시형("절대 금지", "즉시 매도")보다 상태 서술형으로 쓴다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TradingNarrative {

    private record Action(String title, String body, String tone) {}

    public static Narrative build(ChartPhase phase, MarketContext ctx, Verdict verdict, EntrySetup plan) {
        boolean us = isUsMarket(ctx);
        List<Narrative.Detail> details = new ArrayList<>();
        String summary = summarize(phase, ctx, us, details);
        Action action = action(phase, ctx, verdict, plan, us);
        return new Narrative(headline(phase, ctx), summary, List.copyOf(details),
                action.title(), action.body(), action.tone());
    }

    // ------------------------------------------------------------------
    // 1. 한 줄 요약 (제목)
    // ------------------------------------------------------------------
    private static String headline(ChartPhase phase, MarketContext ctx) {
        double rsi = ctx.momentum().rsi();
        double adx = ctx.momentum().adx();
        double volRatio = volumeRatio(ctx);
        double price = ctx.currentPrice();
        var ma = ctx.movingAverages();

        return switch (phase) {
            case DANGER_ZONE -> {
                if (volRatio > 2.0) yield "대량 매도 거래량이 동반된 하락 추세 구간";
                if (rsi < 30) yield "과매도권에 진입한 하락 추세 구간";
                yield "주요 이평선 아래에서 약세가 이어지는 하락 국면";
            }
            case DOWNTREND_CONFIRMED -> "고점과 저점이 함께 낮아지며 굳어진 하락 추세";
            case BEAR_RALLY -> ctx.macd().isGoldenCross()
                    ? "하락장 속 MACD 골든크로스 (단기 반등 여부 확인 필요)"
                    : "큰 추세는 하락인 상태에서 나온 단기 반등";
            case CAPITULATION -> volRatio > 3.0
                    ? "큰 거래량을 동반한 투매 구간 (바닥 확정은 아님)"
                    : "핵심 지지선 이탈과 함께 매도가 쏟아지는 구간";
            case RECOVERY_WATCH -> {
                if (ctx.macd().isGoldenCross()) yield "MACD 골든크로스가 나온 바닥 다지기 초기 구간";
                if (price > ma.ma5()) yield "5일선을 회복하며 추세 전환 여부를 시험하는 구간";
                yield "하락 이후 저점을 높이며 회복 가능성을 확인하는 초기 구간";
            }
            case WATCH -> {
                if (ctx.bollingerBand().isSqueeze()) yield "볼린저 밴드가 수축한 방향 대기 구간";
                if (price < ma.ma20()) yield "20일선 아래에서 방향을 탐색하는 관망 구간";
                if (ctx.priceAction().isSideways()) yield "뚜렷한 우위 없이 횡보 중인 관망 구간";
                yield "방향 확인이 더 필요한 관망 구간";
            }
            case SETUP -> Math.abs(ma.distFromMa20()) < ctx.momentum().atr() * 0.5
                    ? "20일선 부근에서 에너지를 모으는 눌림목 후보 구간"
                    : "상승 구조 속에서 변동성이 줄어드는 준비 구간";
            case ENTRY_READY -> "지지 또는 저항 부근에서 매수세가 확인되는 진입 관찰 구간";
            case FAKE_OUT -> "저항 돌파 후 다시 밀린 불트랩 가능성 구간";
            case STOP_LOSS -> "핵심 지지선을 이탈해 상승 시나리오가 훼손된 구간";
            case STRONG_HOLD -> {
                if (adx > 40) yield "추세 강도(ADX)가 매우 높은 강한 상승 구간";
                if (ctx.bollingerBand().percentB() > 0.8) yield "볼린저 상단 부근에서 시세를 이어가는 상승 구간";
                yield "상승 추세가 유지되는 보유 구간";
            }
            case COOLDOWN -> "상승 이후 열기를 식히며 20일선과의 이격을 줄이는 조정 구간";
            case RE_ENTRY -> "20일선 부근에서 지지를 확인하고 다시 오르려는 재진입 후보 구간";
            case WARNING_ZONE -> rsi > 80
                    ? "RSI 80을 넘긴 단기 과열 구간"
                    : "과열 신호가 겹쳐 차익 실현 매물이 나오기 쉬운 구간";
            case CLIMAX -> "과열이 극단에 이른 정점 부근 구간";
            case SCALE_OUT -> ctx.volume().isVolBearish()
                    ? "고점 부근에서 매도 거래량이 늘며 탄력이 둔화된 구간"
                    : "과열 부담과 모멘텀 둔화로 수익 관리가 필요한 구간";
            default -> "차트 구조를 분석하고 있습니다";
        };
    }

    // ------------------------------------------------------------------
    // 2. 상황 설명 (본문 + 세부 항목)
    // ------------------------------------------------------------------
    private static String summarize(ChartPhase phase, MarketContext ctx, boolean us,
                                    List<Narrative.Detail> d) {
        var vol = ctx.volume();
        var ma = ctx.movingAverages();
        var mom = ctx.momentum();
        double price = ctx.currentPrice();
        double rsi = mom.rsi();
        double adx = mom.adx();
        double volRatio = volumeRatio(ctx);

        switch (phase) {
            case DANGER_ZONE, DOWNTREND_CONFIRMED -> {
                if (vol.isVolBearish()) {
                    add(d, "📉", "매도 거래량 우세", "하락할 때 거래량이 늘고 있어 바닥을 예측하기 어려운 상태입니다.");
                }
                if (rsi < 30) {
                    add(d, "🧊", String.format("RSI %.1f", rsi),
                            "과매도권이지만 하락장에서는 과매도가 오래 이어질 수 있어 반등 신호로 단정하기 어렵습니다.");
                }
                return "큰 추세가 상승이 아니고 뚜렷한 회복 신호가 없는 구간입니다.";
            }
            case BEAR_RALLY -> {
                if (adx >= 30) {
                    add(d, "⚠️", "하락 추세 강도", String.format(
                            "ADX가 %.1f로 높아 반등 뒤 이전 저점을 다시 시험할 가능성이 남아 있습니다.", adx));
                }
                if (vol.isVolBearish()) {
                    add(d, "📉", "저항에서 매물", "반등 구간에서 매도 거래량이 우세해 위쪽 저항에서 물량이 나오는 모습입니다.");
                }
                return "큰 추세는 하락인데 단기 상승 구조가 나타났습니다. 추세 전환이 확인되기 전까지는 반등의 한계를 염두에 둘 필요가 있습니다.";
            }
            case STOP_LOSS -> {
                double s2 = ctx.pivots().dailyPivot().s2();
                add(d, "🚨", "지지 이탈", String.format(
                        "현재가 %s이(가) 2차 지지선(S2) %s 아래에 있습니다.", fmt(price, us), fmt(s2, us)));
                return "핵심 지지선이 무너져 기존 상승 시나리오의 근거가 약해진 구간입니다.";
            }
            case FAKE_OUT -> {
                add(d, "🪤", "추격 매수 물량", "돌파를 보고 들어온 물량이 매물로 바뀌면 단기 하방 압력이 커질 수 있습니다.");
                return "저항선을 넘는 듯했으나 대량의 매도에 다시 밀려 내려온 불트랩 가능성이 있습니다.";
            }
            case CAPITULATION -> {
                if (volRatio >= 3.0) {
                    add(d, "🔥", "거래량 급증", String.format(
                            "5일 평균의 %.1f배 거래량입니다. 항복성 매도의 정점일 수 있으나 추가 하락 가능성도 남아 있습니다.", volRatio));
                }
                if (rsi < 30) {
                    add(d, "🧊", String.format("RSI %.1f", rsi), "극단적인 과매도 상태입니다.");
                }
                add(d, "🔎", "바닥 확인 조건", "투매 직후 바로 바닥이 되는 경우는 드뭅니다. 반등 후 재하락 시 저점이 지켜지는지(더 높은 저점)가 확인 포인트입니다.");
                return "가격 충격과 매도 거래량이 겹친 투매 구간입니다. 바닥이 확인된 것은 아닙니다.";
            }
            case COOLDOWN -> {
                add(d, "🧱", "핵심 지지", String.format("20일선(%s)이 지켜지는지가 핵심입니다.", fmt(ma.ma20(), us)));
                if (vol.isVolumeDryingUp()) {
                    add(d, "🔇", "거래량 감소", "조정 중 거래량이 줄어 매도 압력이 약해지고 있을 가능성이 있습니다.");
                }
                return "상승 추세 속에서 20일선 부근까지 내려와 숨을 고르는 조정 구간입니다.";
            }
            case WARNING_ZONE -> {
                if (ctx.bollingerBand().percentB() > 1.0) {
                    add(d, "🔥", "볼린저 상단 이탈", "강한 추세의 신호일 수 있으나 밴드 안으로 되돌리려는 단기 조정 압력도 커집니다.");
                }
                if (rsi >= 70) {
                    add(d, "📈", String.format("RSI %.1f", rsi), "단기 과열권에 들어와 있습니다.");
                }
                if (ma.distFromMa20() > 0) {
                    add(d, "📏", "20일선 이격", String.format("20일선 대비 %+.1f%% 벌어져 있습니다.", ma.distFromMa20()));
                }
                return "과열 신호가 여러 개 겹쳐 차익 실현 매물이 나오기 쉬운 구간입니다.";
            }
            case CLIMAX -> {
                add(d, "🌡️", String.format("RSI %.1f", rsi), "과열이 극단 수준이고 소진 신호(약세 거래량 또는 고점 하락)가 함께 나타났습니다.");
                if (volRatio >= 3.0) {
                    add(d, "🔥", "거래량 폭증", "고점 부근의 폭증 거래량은 물량 교체가 이뤄지는 신호일 수 있습니다.");
                }
                return "가격이 가파르게 오른 뒤 정점 부근에서 변동성이 커진 구간입니다.";
            }
            case SCALE_OUT -> {
                List<String> why = new ArrayList<>();
                if (rsi >= 70) why.add("RSI 과열권");
                if (vol.isVolBearish()) why.add("매도 거래량 출회");
                if (!ctx.macd().isHistogramRising()) why.add("MACD 모멘텀 감소");
                if (!why.isEmpty()) {
                    add(d, "⚠️", "둔화 신호", String.join(" · ", why) + "이(가) 확인됩니다.");
                }
                if (adx >= 30) {
                    add(d, "💡", "추세 강도", String.format(
                            "ADX가 %.1f로 여전히 높아 급락보다는 횡보나 쌍봉 형성 후 방향을 다시 잡을 수 있습니다.", adx));
                }
                return "상승 엔진은 아직 꺼지지 않았지만 탄력이 눈에 띄게 둔화되기 시작했습니다.";
            }
            case STRONG_HOLD -> {
                if (price >= ctx.priceSeries().yesterdayPrice() * 1.03) {
                    add(d, "🚀", "갭 상승", "전일 대비 3% 이상 갭을 열며 상방 압력이 강하게 나왔습니다.");
                }
                if (adx >= 30) {
                    add(d, "📈", "추세 강도", String.format("ADX %.1f로 상승 모멘텀이 유지되고 있습니다.", adx));
                }
                if (ma.distFromMa20() > mom.atr() * 2.0) {
                    add(d, "📏", "이격 확대", String.format(
                            "20일선과 이격이 커졌습니다. 5일선(%s)이 보유 기준선 역할을 하는지 지켜볼 구간입니다.", fmt(ma.ma5(), us)));
                } else {
                    add(d, "🧱", "보유 기준", String.format(
                            "5일선과 20일선(%s)이 유지되는 동안 상승 구조가 이어집니다.", fmt(ma.ma20(), us)));
                }
                return "매수세가 매도 물량을 소화하며 주가가 우상향하는 구간입니다.";
            }
            case ENTRY_READY -> {
                if (vol.isVolBullish()) {
                    add(d, "💰", "매수 거래량", "의미 있는 매수 거래량이 반등에 힘을 싣고 있습니다.");
                }
                if (ctx.macd().isGoldenCross()) {
                    add(d, "✨", "MACD 골든크로스", "상승 모멘텀이 살아나는 신호가 나왔습니다.");
                }
                return "저항 돌파 직전이거나 지지를 딛고 일어서는 진입 관찰 구간입니다.";
            }
            case RE_ENTRY -> {
                if (vol.isVolBullish()) {
                    add(d, "💰", "매수 거래량", "조정 뒤 매수세가 다시 들어오고 있습니다.");
                }
                return "1차 상승 이후 조정을 마치고 VWAP(20) 부근에서 다시 일어서는 구간입니다.";
            }
            case SETUP -> {
                if (vol.isVolumeDryingUp()) {
                    add(d, "🔇", "거래량 감소", "조정 중 거래량이 말라 매도 압력이 약해진 상태일 수 있습니다.");
                } else if (vol.isVolBearish()) {
                    add(d, "⚠️", "매도 거래량", String.format(
                            "음봉에 거래량이 실렸습니다. 20일선(%s) 지지 여부를 끝까지 확인해야 합니다.", fmt(ma.ma20(), us)));
                }
                if (ctx.bollingerBand().isSqueeze()) {
                    add(d, "🌪️", "밴드 수축", "변동성이 줄어든 뒤에는 커지는 경향이 있으나, 방향은 돌파·이탈이 나와야 알 수 있습니다.");
                }
                return "상승 구조 속에서 숨을 고르며 다음 움직임을 준비하는 구간입니다.";
            }
            case RECOVERY_WATCH -> {
                if (ctx.pivots().dailyPivot() != null && price > ctx.pivots().dailyPivot().p()) {
                    add(d, "📈", "피봇 회복", String.format("일일 피봇 중심선(%s) 위로 올라서며 단기 심리가 개선되고 있습니다.",
                            fmt(ctx.pivots().dailyPivot().p(), us)));
                }
                add(d, "🔎", "확인 포인트", "하루짜리 반등이 아니라, 반등 → 재조정 → 더 높은 저점(HL) 순서로 이어지는지가 중요합니다.");
                return "하락 이후 저점을 높이며 바닥을 다지는 초기 회복 국면입니다.";
            }
            case WATCH -> {
                if (ctx.bollingerBand().isSqueeze()) {
                    add(d, "🌪️", "변동성 수축", "볼린저 밴드가 좁아진 상태입니다. 변동성이 커질 가능성은 있지만 방향은 아직 정해지지 않았습니다.");
                }
                if (adx < 20) {
                    add(d, "💤", "추세 부재", String.format(
                            "추세 강도(ADX)가 %.1f로 낮습니다. 방향 없이 횡보할 때 진입하면 자금이 오래 묶일 수 있습니다.", adx));
                }
                if (price < ma.ma20()) {
                    add(d, "📉", "하방 압력", String.format(
                            "주가가 20일선(%s) 아래에 있어 박스권 하단을 이탈할 위험도 열어두어야 합니다.", fmt(ma.ma20(), us)));
                } else {
                    add(d, "⚠️", "가짜 신호 주의", "20일선 위에 있지만 거래량과 추세 강도가 받쳐주지 않으면 되돌림이 나올 수 있습니다.");
                }
                return "뚜렷한 매수·매도 주체 없이 눈치싸움이 이어지는 관망 국면입니다.";
            }
            default -> {
                return "가격과 지표 데이터를 바탕으로 차트 구조를 분석하고 있습니다.";
            }
        }
    }

    // ------------------------------------------------------------------
    // 3. 대응 안내 (진입 계획이 있으면 계획 기준, 없으면 국면 기준)
    // ------------------------------------------------------------------
    private static Action action(ChartPhase phase, MarketContext ctx, Verdict verdict, EntrySetup plan, boolean us) {
        if (plan != null && verdict != null && verdict != Verdict.NO_SETUP) {
            String zone = fmt(plan.entry().low(), us) + " ~ " + fmt(plan.entry().high(), us);
            String stop = fmt(plan.stop(), us);
            String rr = plan.hasStructuralTarget() ? String.format(" 1차 목표 %s, 손익비 %.1f.", fmt(plan.target1(), us), plan.rr()) : "";
            String type = plan.type().getTitle();
            switch (verdict) {
                case ACTIONABLE:
                    return new Action("🎯 진입 조건 충족 (" + type + ")",
                            "진입 구간(" + zone + ") 안에서 반등이 확인되고 손익비 조건도 통과했습니다. 구조적 손절 " + stop
                                    + " 아래로 내려가면 이 시나리오는 무효로 봅니다." + rr + " 진입한다면 한 번에 몰기보다 나눠 접근하는 편이 변동성에 유리합니다.",
                            "bull");
                case WAIT_FOR_ZONE:
                    return new Action("🟠 구간 대기 (" + type + ")",
                            "현재가가 눌림 구간(" + zone + ") 위에 있습니다. 구간까지 내려오며 거래량이 줄고 반등 신호가 나오는지 지켜보는 상황입니다. 구조적 손절 기준은 " + stop + "입니다.",
                            "warn");
                case WAIT_TRIGGER:
                    return new Action("🟡 확인 대기 (" + type + ")",
                            "진입 구간(" + zone + ") 안이지만 아직 반등 확인(양봉)이 없습니다. 지지를 확인한 뒤에 판단해도 늦지 않습니다. 손절 기준 " + stop + ".",
                            "neutral");
                case CHASE_RISK:
                    return new Action("⚠️ 추격 주의 (" + type + ")",
                            "이미 진입 구간(" + zone + ")을 벗어나 올라와 있습니다. 지금 들어가면 손절 거리가 길어집니다. 되돌림이 구간 부근까지 오는지 기다리는 편이 유리합니다.",
                            "warn");
                case RR_TOO_LOW:
                    return new Action("⚖️ 손익비 부족 (" + type + ")",
                            "진입 구간은 유효하지만 손절 거리 대비 위쪽 저항이 가까워 기대 수익이 작습니다." + rr + " 이번 자리는 건너뛰는 선택지도 있습니다.",
                            "warn");
                default:
                    break;
            }
        }

        double ma20 = ctx.movingAverages().ma20();
        return switch (phase) {
            case DANGER_ZONE, DOWNTREND_CONFIRMED, STOP_LOSS, BEAR_RALLY, FAKE_OUT ->
                    new Action("🚨 신규 매수 보류",
                            "하방 압력이 우세한 구간이라 신규 진입의 근거가 부족합니다. 물타기보다는 20일선(" + fmt(ma20, us)
                                    + ") 회복과 거래량 감소 후 반전 양봉이 나오는지 확인한 뒤 판단하세요. 보유 중이라면 손절 기준을 다시 점검할 시점입니다.",
                            "bear");
            case WARNING_ZONE, CLIMAX ->
                    new Action("🔥 신규 추격 지양",
                            "과열 구간이라 신규 진입의 손익비가 불리합니다. 보유 중이라면 이익 구간을 지킬 이탈 기준(5일선 또는 직전 지지)을 정해 두는 것을 권합니다.",
                            "warn");
            case SCALE_OUT ->
                    new Action("💰 비중 조절 검토",
                            "상승 탄력이 둔화되는 신호가 보입니다. 수익 구간이라면 일부 비중을 줄이는 방법과, 남은 물량의 이탈 기준을 정하는 방법을 함께 검토해 볼 수 있습니다.",
                            "bear");
            case STRONG_HOLD ->
                    new Action("🛡️ 보유자 유리 구간 (신규는 눌림 대기)",
                            "이미 오른 추세를 따라가는 구간이라 신규 진입은 손절 거리가 길어지기 쉽습니다. 20일선(" + fmt(ma20, us)
                                    + ") 부근 눌림이 오는지 지켜보는 편이 낫습니다.",
                            "bull");
            case COOLDOWN, SETUP, ENTRY_READY, RE_ENTRY ->
                    new Action("👀 진입 구간 관찰",
                            "상승 구조는 유지되지만 현재 기준으로 진입 구간과 손절·목표가 모두 구조적으로 확인되지는 않았습니다. 20일선(" + fmt(ma20, us)
                                    + ") 지지와 거래량 변화를 확인한 뒤 판단하세요.",
                            "neutral");
            case RECOVERY_WATCH, CAPITULATION ->
                    new Action("👀 관망 또는 소액 관찰",
                            "바닥을 다지는 초기 국면으로 추세 전환이 확정된 것은 아닙니다. 5일선(" + fmt(ctx.movingAverages().ma5(), us)
                                    + ") 위 안착과 더 높은 저점이 확인될 때까지 관찰하는 편이 안전합니다.",
                            "neutral");
            case WATCH ->
                    new Action("⏸️ 방향 미확정 (매매 휴식)",
                            ctx.currentPrice() > ma20
                                    ? "20일선(" + fmt(ma20, us) + ") 위에 있지만 거래량과 추세 강도가 약해 신뢰도가 낮습니다. 확실한 매수 거래량이 붙을 때까지 지켜보세요."
                                    : "기회비용을 아끼려면 확실한 매수 거래량과 함께 20일선(" + fmt(ma20, us) + ")을 강하게 되찾는 모습이 나올 때까지 기다리는 편이 낫습니다.",
                            "neutral");
            default ->
                    new Action("⏳ 분석 대기", "차트 데이터가 충분하지 않아 명확한 대응 안내를 만들기 어렵습니다.", "neutral");
        };
    }

    // ------------------------------------------------------------------
    // 유틸
    // ------------------------------------------------------------------
    private static void add(List<Narrative.Detail> list, String icon, String label, String text) {
        list.add(new Narrative.Detail(icon, label, text));
    }

    private static double volumeRatio(MarketContext ctx) {
        double ma5 = ctx.volume().volumeMa5();
        double proj = ctx.volume().projectedVolume();
        return (Double.isFinite(ma5) && ma5 > 0 && Double.isFinite(proj)) ? proj / ma5 : 0.0;
    }

    /** 최근 종가에 소수점이 있거나 가격이 작으면 달러(미국 주식)로 본다. */
    private static boolean isUsMarket(MarketContext ctx) {
        List<Double> closes = ctx.priceSeries().closes();
        if (closes != null && !closes.isEmpty()) {
            int start = Math.max(0, closes.size() - 10);
            for (int i = start; i < closes.size(); i++) {
                Double c = closes.get(i);
                if (c != null && c != Math.floor(c)) return true;
            }
        }
        return ctx.currentPrice() < 5000.0;
    }

    private static String fmt(double v, boolean us) {
        return us ? String.format("$%,.2f", v) : String.format("%,.0f원", v);
    }
}