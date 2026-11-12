package com.stocklog.modules.stock.signal;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 신호 결과 계산 규칙(track-v1) 고정 테스트 */
class SignalOutcomesTest {

    private static final LocalDate D0 = LocalDate.of(2026, 1, 1);

    /** 시가 = 종가 = c, 고가 = c+1, 저가 = c-1 */
    private static List<SignalOutcomes.Bar> bars(double... closes) {
        List<SignalOutcomes.Bar> out = new ArrayList<>();
        for (int i = 0; i < closes.length; i++) out.add(new SignalOutcomes.Bar(D0.plusDays(i), closes[i], closes[i] + 1, closes[i] - 1, closes[i]));
        return out;
    }

    @Test
    void 신호_다음날_시가에_체결하고_신호일_이전_가격은_쓰지_않는다() {
        double[] c = new double[30];
        for (int i = 0; i < 30; i++) c[i] = 100 + i;   // 꾸준히 오름
        List<SignalOutcomes.Bar> b = bars(c);
        SignalOutcomes.Outcome o = SignalOutcomes.compute(b, D0.plusDays(2), 2.0, 0, 0, 0.0);
        assertEquals(D0.plusDays(3), o.entryDate());
        assertEquals(103.0, o.entryOpen());
        assertEquals(107.0 / 103 - 1, o.ret5(), 1e-9);           // 체결일 포함 5번째 봉 종가
        assertEquals(122.0 / 103 - 1, o.ret20(), 1e-9);
        assertEquals(123.0 / 103 - 1, o.mfe20(), 1e-9);           // 20봉 중 최고 고가 122+1
    }

    @Test
    void 봉이_모자라면_PARTIAL_이고_20일_결과는_비운다() {
        SignalOutcomes.Outcome o = SignalOutcomes.compute(bars(100, 101, 102, 103, 104, 105, 106), D0, 2.0, 0, 0, 0.0);
        assertEquals("PARTIAL", o.status());
        assertNotNull(o.ret5());
        assertNull(o.ret20());
        assertNull(o.x20Net());
    }

    @Test
    void 신호_다음_봉이_없으면_PENDING() {
        SignalOutcomes.Outcome o = SignalOutcomes.compute(bars(100, 101), D0.plusDays(1), 2.0, 0, 0, 0.0);
        assertEquals("PENDING", o.status());
        assertNull(o.entryDate());
    }

    @Test
    void 보호_손절은_종가_이탈_다음날_시가에_비용을_빼고_청산() {
        // 체결 100, ATR 2 → 손절 96. 3번째 봉 종가 95로 이탈 → 다음 봉 시가 94
        List<SignalOutcomes.Bar> b = bars(100, 100, 99, 95, 94, 94, 94, 94, 94, 94, 94, 94, 94, 94, 94, 94, 94, 94, 94, 94, 94, 94, 94, 94);
        SignalOutcomes.Outcome o = SignalOutcomes.compute(b, D0.minusDays(1), 2.0, 0, 0, 0.0035);
        assertEquals(94.0 / 100 * (1 - 0.0035) - 1, o.x10Net(), 1e-9);
        assertEquals(94.0 / 100 * (1 - 0.0035) - 1, o.x20Net(), 1e-9);
    }

    @Test
    void 손절가와_목표가가_같은_날_닿으면_손절을_먼저로_본다() {
        List<SignalOutcomes.Bar> b = new ArrayList<>();
        b.add(new SignalOutcomes.Bar(D0, 100, 100, 100, 100));
        b.add(new SignalOutcomes.Bar(D0.plusDays(1), 100, 111, 89, 100));   // 체결일: 손절 90 · 목표 110 동시
        for (int i = 2; i < 25; i++) b.add(new SignalOutcomes.Bar(D0.plusDays(i), 100, 100.5, 99.5, 100));
        SignalOutcomes.Outcome o = SignalOutcomes.compute(b, D0, 2.0, 90, 110, 0.0);
        assertEquals("STOP", o.firstHit());
        assertTrue(o.stopHit20());
        assertTrue(o.targetHit20());
    }

    @Test
    void 비용은_한국과_미국을_구분한다() {
        assertEquals(0.0028, SignalOutcomes.costOf("005930"));
        assertEquals(0.0035, SignalOutcomes.costOf("AAPL"));
    }

    @Test
    void 주_단위_묶음_t는_주가_3개_미만이면_계산하지_않는다() {
        double[] r = SignalStatsService.clusterT(Map.of(1L, List.of(0.01, 0.02), 2L, List.of(0.03)));
        assertEquals(0.02, r[0], 1e-12);
        assertTrue(Double.isNaN(r[1]));
        double[] r2 = SignalStatsService.clusterT(Map.of(1L, List.of(0.01), 2L, List.of(0.02), 3L, List.of(0.03)));
        assertEquals(0.02, r2[0], 1e-12);
        assertEquals(0.02 / Math.sqrt(0.0001 / 3), r2[1], 1e-9);
    }
}
