package com.stocklog.modules.stock.analysis.signal;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** 분석기 시그널 문장을 화면용으로 나눈 것 */
public record SignalView(
        String title,   // 무엇이 보였는지 (이모지·말머리 제거)
        String detail,  // 그래서 어떻다는 건지 ("—" 뒤), 없으면 null
        String desc,    // 풀어 쓴 설명, 없으면 null
        Tone tone
) {

    public enum Tone {
        GOOD("좋음"), BAD("주의"), NEUTRAL("참고");

        private final String label;
        Tone(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private static final String NO_DESC_PREFIX = "현재 설정된 조건에 매칭되는 상세 해설이 없습니다";

    /** 앞일수록 먼저 본다: 경고 표시 → 나쁜 단어 → 중립 단어 → 좋은 단어 */
    private static final List<String> BAD_MARKS = List.of("⚠️", "⚠", "🚨", "[경고]", "[주의]");
    private static final List<String> GOOD_MARKS = List.of("🚀", "🔥");
    private static final List<String> BAD_WORDS = List.of(
            "하락", "약세", "약함", "약화", "역배열", "데드크로스", "과열", "과매수", "취약", "역성장", "부담",
            "적자", "잠식", "압박", "고평가", "다소 높음", "대비 높음", "과다", "정당화 불가", "리스크", "경계",
            "관망", "급등락", "출렁임", "신저가", "마이너스", "설거지");
    private static final List<String> NEUTRAL_WORDS = List.of(
            "부족", "부재", "누락", "보류", "확인 필요", "자산군 전용", "평이한", "평소 수준", "중간권", "횡보",
            "적정", "부합", "대기", "중형", "중배당");
    private static final List<String> GOOD_WORDS = List.of(
            "상승", "우수", "성장", "지지", "골든", "정배열", "저평가", "저렴", "확보", "지배력", "안정적",
            "고효율", "준수", "매집", "신고가", "고가권", "견조", "대형", "고배당", "회복", "돌파", "안착");

    public static SignalView of(String raw) {
        String text = raw == null ? "" : raw.trim();
        Tone tone = toneOf(text);

        String cleaned = stripMarks(text);
        String title = cleaned, detail = null;
        int dash = cleaned.indexOf(" — ");
        if (dash > 0) {
            title = cleaned.substring(0, dash).trim();
            detail = cleaned.substring(dash + 3).trim();
            if (detail.isEmpty()) detail = null;
        }

        String desc = SignalDescription.get(text);
        if (desc == null || desc.isBlank() || desc.startsWith(NO_DESC_PREFIX)) desc = null;

        return new SignalView(title, detail, desc, tone);
    }

    /** 좋음 → 주의 → 참고 순 */
    public static List<SignalView> listOf(List<String> signals) {
        if (signals == null) return List.of();
        return signals.stream()
                .filter(Objects::nonNull)
                .map(SignalView::of)
                .sorted(Comparator.comparingInt(v -> v.tone().ordinal()))
                .toList();
    }

    public static long count(List<SignalView> views, Tone tone) {
        return views.stream().filter(v -> v.tone() == tone).count();
    }

    static Tone toneOf(String text) {
        if (BAD_MARKS.stream().anyMatch(text::startsWith)) return Tone.BAD;
        if (GOOD_MARKS.stream().anyMatch(text::startsWith)) return Tone.GOOD;
        if (BAD_WORDS.stream().anyMatch(text::contains)) return Tone.BAD;
        if (NEUTRAL_WORDS.stream().anyMatch(text::contains)) return Tone.NEUTRAL;
        if (GOOD_WORDS.stream().anyMatch(text::contains)) return Tone.GOOD;
        return Tone.NEUTRAL;
    }

    /** 앞에 붙은 이모지·말머리([경고], ※ 등)를 떼어 낸다 */
    private static String stripMarks(String s) {
        String r = s;
        boolean changed = true;
        while (changed && !r.isEmpty()) {
            changed = false;
            String before = r;
            r = r.replaceFirst("^\\[[^\\]]{1,6}\\]\\s*", "");                 // 경고 [주의]
            r = r.replaceFirst("^[※*·•\\-]\\s*", "");                          // ※ 말머리
            int cp = r.isEmpty() ? 0 : r.codePointAt(0);
            if (cp != 0 && !Character.isLetterOrDigit(cp) && !Character.isWhitespace(cp) && cp != '(' && cp != '[') {
                r = r.substring(Character.charCount(cp));                        // 이모지 한 글자
                r = r.replaceFirst("^\\uFE0F", "");                               // 이모지 변형 선택자
                r = r.trim();
            }
            changed = !r.equals(before);
        }
        return r.isEmpty() ? s : r;
    }

    public String toneKey() {
        return tone.name().toLowerCase(Locale.ROOT);
    }
}
