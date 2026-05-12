package com.stocklog.domain.marketdata.analysis;

import com.stocklog.domain.marketdata.index.dto.IndexDashboardDto;
import com.stocklog.domain.marketdata.index.dto.IndexDto;
import com.stocklog.domain.marketdata.universe.dto.UniverseDashboardDto;
import com.stocklog.domain.marketdata.universe.dto.UniverseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MarketDataAnalyzer {

    public MarketTrendResult analyze(IndexDashboardDto index, UniverseDashboardDto universe) {
        List<String> signals = new ArrayList<>();
        double totalScore = 0.0;
        double totalWeight = 0.0;

        // 1. 거시 경제 및 지수 추세 분석 (가중치 50%)
        if (index != null) {
            totalScore += analyzeMacro(index, signals) * 0.50;
            totalWeight += 0.50;
        }

        // 2. 섹터별 자금 흐름 상대 비교 분석 (가중치 50%)
        if (universe != null) {
            totalScore += analyzeSectorBreadth(universe, signals) * 0.50;
            totalWeight += 0.50;
        }

        // 데이터 누락 시 방어 로직
        if (totalWeight == 0.0) {
            signals.add("⚠️ 데이터 수집 지연 (분석 보류)");
            return new MarketTrendResult(50, "Neutral (데이터 대기)", signals);
        }

        int finalScore = (int) Math.round(totalScore / totalWeight);
        finalScore = Math.max(0, Math.min(100, finalScore)); // 0 ~ 100 클램핑

        return new MarketTrendResult(finalScore, determineRegime(finalScore), signals);
    }

    // ── 1. 매크로(Index) 분석 로직 ──
    private double analyzeMacro(IndexDashboardDto index, List<String> signals) {
        double score = 50.0; // 기본 점수 (중립)

        // VIX (투자 심리) - 점수가 높을수록 좋음
        if (isValid(index.vix())) {
            double vixValue = getLastValue(index.vix());
            if (vixValue >= 30.0) {
                signals.add("🚨 VIX 30 상회 (극단적 공포 및 투매 구간)");
                score -= 30.0; // 시장 점수 대폭 하락
            } else if (vixValue < 15.0) {
                signals.add("🟢 VIX 15 하회 (투자 심리 안정 및 강세 지지)");
                score += 20.0; // 시장 점수 상승
            }
        }

        // 장단기 금리차 (침체 시그널)
        if (isValid(index.yieldCurve())) {
            double spread = getLastValue(index.yieldCurve());
            if (spread < 0.0) {
                signals.add("⚠️ 장단기 금리 역전 지속 (경기 침체 선행 시그널)");
                score -= 15.0; // 시장 점수 하락
            }
        }

        // 달러 인덱스 (글로벌 유동성)
        if (isValid(index.dxy())) {
            double dxyChange = getChangeRate(index.dxy());
            if (dxyChange > 1.5) {
                signals.add("⚠️ 달러 강세 급등 (위험 자산 유동성 축소 우려)");
                score -= 15.0; // 시장 점수 하락
            }
        }

        return Math.max(0.0, Math.min(100.0, score));
    }

    // ── 2. 섹터(Universe) 상대 비교 분석 로직 ──
    private double analyzeSectorBreadth(UniverseDashboardDto universe, List<String> signals) {
        double score = 50.0; // 기본 점수 (중립)

        boolean isTechStrong = isPhaseBullish(universe.xlk());
        boolean isDefensiveStrong = isPhaseBullish(universe.xlp());

        boolean isTechWeak = isPhaseBearish(universe.xlk());
        boolean isDefensiveWeak = isPhaseBearish(universe.xlp());

        // 기술주(XLK - 위험자산) vs 필수소비재(XLP - 안전자산) 자금 이동 분석
        if (isTechStrong && isDefensiveWeak) {
            signals.add("🔥 기술주 강세 · 방어주 약세 (완벽한 Risk-On 장세)");
            score = 90.0; // 시장 점수 매우 높음 (공격 투자)
        } else if (isTechWeak && isDefensiveStrong) {
            signals.add("🚨 기술주 이탈 · 방어주 피신 (전형적인 Risk-Off 장세)");
            score = 20.0; // 시장 점수 매우 낮음 (방어 투자)
        } else if (isTechStrong && isDefensiveStrong) {
            signals.add("🟡 성장주와 방어주 동반 강세 (유동성 장세 및 과열 주의)");
            score = 60.0;
        } else if (isTechWeak && isDefensiveWeak) {
            signals.add("🧊 전 섹터 자금 이탈 (현금 관망 절대 필요)");
            score = 10.0; // 투매 구간
        } else {
            signals.add("🔄 섹터 간 순환매 진행 (지수 방향성 탐색 중)");
            score = 50.0;
        }

        // 인프라/산업(XLI) 및 에너지(XLE)를 통한 실물 경기 판단 보조
        if (isPhaseBullish(universe.xli()) && isPhaseBullish(universe.xle())) {
            signals.add("📈 산업재·에너지 동반 상승 (실물 경기 회복 시그널)");
            score = Math.min(100.0, score + 15.0);
        } else if (isPhaseBearish(universe.xli())) {
            signals.add("⚠️ 산업재 약세 (실물 경기 둔화 우려 반영)");
            score = Math.max(0.0, score - 15.0);
        }

        return score;
    }

    // ── 국면(Regime) 판별 ──
    private String determineRegime(int score) {
        if (score >= 85) return "Strong Risk-On (극단적 강세 및 적극 편승)";
        if (score >= 70) return "Risk-On (강세장 및 비중 확대 유효)";
        if (score >= 55) return "Cautious Bull (강보합 및 눌림목 매수 권장)";
        if (score >= 40) return "Neutral (방향성 탐색 및 박스권 대응)";
        if (score >= 25) return "Cautious Bear (약보합 및 리스크 관리 시작)";
        if (score >= 10) return "Risk-Off (약세장 및 현금 비중 확대)";
        return "Panic (극단적 공포 및 투매 동참 금지)";
    }

    // ── 헬퍼 메서드 (IndexDto 내부 구조에 맞게 데이터 추출) ──
    private boolean isValid(IndexDto dto) {
        // 실제 IndexDto의 데이터 리스트 존재 여부를 확인하는 로직에 맞춰 수정하세요.
        // 예: return dto != null && dto.data() != null && !dto.data().isEmpty();
        return dto != null;
    }

    private double getLastValue(IndexDto dto) {
        // 실제 IndexDto에서 가장 최신 값을 가져오는 로직에 맞춰 수정하세요.
        // 예: return dto.data().get(dto.data().size() - 1).value().doubleValue();
        return 0.0; // 임시 반환값
    }

    private double getChangeRate(IndexDto dto) {
        // 실제 IndexDto에서 변동률을 가져오는 로직에 맞춰 수정하세요.
        // 예: return dto.changeRate() != null ? dto.changeRate().doubleValue() : 0.0;
        return 0.0; // 임시 반환값
    }

    // ── 헬퍼 메서드 (UniverseDto Phase 체크) ──
    private boolean isPhaseBullish(UniverseDto dto) {
        if (dto == null || dto.phase() == null) return false;
        String phase = dto.phase().name(); // Enum일 경우 name(), String일 경우 phase() 그대로 사용
        return phase.equals("ENTRY_READY") || phase.equals("STRONG_HOLD");
    }

    private boolean isPhaseBearish(UniverseDto dto) {
        if (dto == null || dto.phase() == null) return false;
        String phase = dto.phase().name();
        return phase.equals("DANGER_ZONE") || phase.equals("COOLDOWN");
    }
}