package com.stocklog.global.view;

import org.springframework.stereotype.Component;

import java.util.Map;

/** 화면에 보여 줄 문장에서 이모지를 덜어 낸다 */
@Component("emojiText")
public class EmojiText {

    /** 비슷한 뜻은 하나로 모아 화면에 쓰는 이모지 종류를 줄인다 */
    private static final Map<String, String> UNIFY = Map.of(
            "🟠", "⏳", "🟡", "⏳",
            "🦵", "🎯",
            "🚨", "⚠️", "🔥", "⚠️"
    );

    public String strip(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> { if (!isEmojiPart(cp)) sb.appendCodePoint(cp); });
        return sb.toString().replaceAll("\\s{2,}", " ").trim();
    }

    /** 문장 맨 앞 이모지 (없으면 빈 문자열) */
    public String lead(String text) {
        if (text == null) return "";
        String t = text.trim();
        int end = 0;
        while (end < t.length()) {
            int cp = t.codePointAt(end);
            if (!isEmojiPart(cp)) break;
            end += Character.charCount(cp);
        }
        if (end == 0) return "";
        String e = t.substring(0, end);
        String base = e.replace("️", "");
        return UNIFY.getOrDefault(base, UNIFY.getOrDefault(e, e));
    }

    static boolean isEmojiPart(int cp) {
        return (cp >= 0x1F000 && cp <= 0x1FAFF)   // 그림 문자·국기·기호
                || (cp >= 0x2600 && cp <= 0x27BF)  // 기타 기호·딩뱃 ( 등)
                || cp == 0x2B50 || cp == 0x231B || cp == 0x23F0 || (cp >= 0x23E9 && cp <= 0x23FA)
                || cp == 0xFE0F || cp == 0x200D || cp == 0x20E3;
    }
}
