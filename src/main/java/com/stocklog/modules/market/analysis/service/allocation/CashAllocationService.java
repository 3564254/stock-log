package com.stocklog.modules.market.analysis.service.allocation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocklog.modules.market.analysis.diagnosis.MarketDiagnosisHistory;
import com.stocklog.modules.market.analysis.diagnosis.MarketDiagnosisHistoryRepository;
import com.stocklog.modules.market.analysis.dto.EngineInput;
import com.stocklog.modules.market.analysis.service.allocation.CashAllocationCalculator.Raw;
import com.stocklog.modules.market.analysis.service.allocation.CashAllocationCalculator.Reason;
import com.stocklog.modules.market.analysis.service.result.EngineScores;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 권장 현금 비중 */
@Service
@RequiredArgsConstructor
public class CashAllocationService {

    private static final int TREND_DAYS = 20;

    private final MarketDiagnosisHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    public record Point(LocalDate date, double pct) {}

    public record CashGuide(
            double targetPct,           // 권장 현금 비중 (5% 단위)
            Double previousPct,         // 전 거래일 권장 비중 (기록이 없으면 null)
            double rawPct,              // 하루 변화 제한 전 값
            boolean limited,            // 하루 변화 제한이 걸렸는지
            String stance,              // 적극 / 중립 / 방어 / 매우 방어
            double coreScore,
            int confidence,
            List<Reason> reasons,
            List<Point> trend,          // 최근 20개 기록 + 오늘 (오래된 순)
            LeverageGate.LeverageGuide leverage  // 레버리지 허용 여부 (허용 / 보류 / 비추천)
    ) {
        public Double change() { return previousPct == null ? null : targetPct - previousPct; }
        public double investPct() { return 100 - targetPct; }
    }

    @Transactional(readOnly = true)
    public CashGuide evaluate(EngineInput input) {
        LocalDate today = LocalDate.now();
        List<MarketDiagnosisHistory> past = new ArrayList<>(historyRepository.findTop120ByDateBeforeOrderByDateDesc(today));
        Collections.reverse(past);

        Double prev = null;
        int streak = 0; // 레버리지 필수 조건 연속 충족 일수
        List<Point> points = new ArrayList<>();
        for (MarketDiagnosisHistory row : past) {
            Map<String, Double> s = EngineScores.fromJson(objectMapper, row.getRawEngineInputJson());
            if (s.isEmpty()) continue;
            prev = CashAllocationCalculator.limit(CashAllocationCalculator.raw(s), prev);
            points.add(new Point(row.getDate(), prev));
            streak = LeverageGate.requiredPass(s) ? streak + 1 : 0;
        }

        Map<String, Double> todayScores = EngineScores.of(input);
        Raw raw = CashAllocationCalculator.raw(todayScores);
        double target = CashAllocationCalculator.limit(raw, prev);
        points.add(new Point(today, target));
        List<Point> trend = points.subList(Math.max(0, points.size() - (TREND_DAYS + 1)), points.size());
        streak = LeverageGate.requiredPass(todayScores) ? streak + 1 : 0;
        LeverageGate.LeverageGuide leverage = LeverageGate.evaluate(todayScores, raw.confidence(), target, streak);

        return new CashGuide(target, prev, raw.target(), target != raw.target(),
                CashAllocationCalculator.stanceOf(target), round1(raw.coreScore()), raw.confidence(),
                raw.reasons(), List.copyOf(trend), leverage);
    }

    private static double round1(double v) { return Math.round(v * 10) / 10.0; }
}
