package com.stocklog.modules.stock.analysis.domain.valuation;

import com.stocklog.modules.stock.domain.type.Nation;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class ValuationBand {

    // ── PER 밴드: [저평가 상한, 적정 상한, 고평가 상한] ──────────────────────

    private static final Map<Nation, double[]> DEFAULT_PER_BANDS = Map.of(
            Nation.KR, new double[]{8.0, 14.0, 22.0},
            Nation.US, new double[]{12.0, 22.0, 35.0}
    );

    private static final Map<String, Map<Nation, double[]>> SECTOR_PER_BANDS = Map.ofEntries(
            // 기술 및 성장주
            Map.entry("반도체", Map.of(Nation.KR, new double[]{10.0, 22.0, 38.0}, Nation.US, new double[]{18.0, 35.0, 65.0})),
            Map.entry("소프트웨어 및 IT 서비스", Map.of(Nation.KR, new double[]{15.0, 28.0, 45.0}, Nation.US, new double[]{22.0, 42.0, 75.0})),
            Map.entry("전자상거래 및 플랫폼 소매", Map.of(Nation.KR, new double[]{18.0, 35.0, 60.0}, Nation.US, new double[]{25.0, 50.0, 90.0})),
            Map.entry("하드웨어 및 전자 장비", Map.of(Nation.KR, new double[]{8.0, 16.0, 28.0}, Nation.US, new double[]{14.0, 26.0, 45.0})),

            // 헬스케어
            Map.entry("제약 및 생명공학", Map.of(Nation.KR, new double[]{15.0, 30.0, 55.0}, Nation.US, new double[]{22.0, 45.0, 80.0})),
            Map.entry("의료 기기 및 장비", Map.of(Nation.KR, new double[]{14.0, 26.0, 45.0}, Nation.US, new double[]{20.0, 38.0, 65.0})),
            Map.entry("헬스케어 서비스 및 유통", Map.of(Nation.KR, new double[]{10.0, 20.0, 32.0}, Nation.US, new double[]{14.0, 24.0, 38.0})),

            // 소비재 및 서비스
            Map.entry("식음료 및 담배", Map.of(Nation.KR, new double[]{8.0, 15.0, 24.0}, Nation.US, new double[]{14.0, 22.0, 32.0})),
            Map.entry("가정용품 및 개인용품", Map.of(Nation.KR, new double[]{9.0, 16.0, 26.0}, Nation.US, new double[]{15.0, 24.0, 35.0})),
            Map.entry("내구소비재 및 의류", Map.of(Nation.KR, new double[]{7.0, 14.0, 22.0}, Nation.US, new double[]{12.0, 22.0, 35.0})),
            Map.entry("오프라인 유통 및 소매", Map.of(Nation.KR, new double[]{6.0, 12.0, 18.0}, Nation.US, new double[]{10.0, 18.0, 28.0})),
            Map.entry("외식, 여행 및 레저", Map.of(Nation.KR, new double[]{8.0, 16.0, 28.0}, Nation.US, new double[]{14.0, 26.0, 45.0})),
            Map.entry("미디어 및 엔터테인먼트", Map.of(Nation.KR, new double[]{12.0, 22.0, 38.0}, Nation.US, new double[]{15.0, 28.0, 50.0})),

            // 모빌리티 및 중공업
            Map.entry("순수 자동차 및 모빌리티", Map.of(Nation.KR, new double[]{5.0, 10.0, 18.0}, Nation.US, new double[]{8.0, 16.0, 30.0})),
            Map.entry("우주항공 및 국방", Map.of(Nation.KR, new double[]{10.0, 20.0, 35.0}, Nation.US, new double[]{16.0, 28.0, 45.0})),
            Map.entry("기계 및 산업 장비", Map.of(Nation.KR, new double[]{7.0, 13.0, 22.0}, Nation.US, new double[]{14.0, 24.0, 38.0})),
            Map.entry("운송 및 물류", Map.of(Nation.KR, new double[]{6.0, 12.0, 20.0}, Nation.US, new double[]{12.0, 22.0, 35.0})),

            // 소재 및 에너지
            Map.entry("화학", Map.of(Nation.KR, new double[]{5.0, 11.0, 18.0}, Nation.US, new double[]{9.0, 16.0, 26.0})),
            Map.entry("철강 및 산업용 금속", Map.of(Nation.KR, new double[]{4.0, 9.0, 15.0}, Nation.US, new double[]{7.0, 13.0, 22.0})),
            Map.entry("귀금속 및 특수 광물", Map.of(Nation.KR, new double[]{5.0, 11.0, 20.0}, Nation.US, new double[]{10.0, 18.0, 32.0})),
            Map.entry("전통 화석 에너지", Map.of(Nation.KR, new double[]{5.0, 10.0, 17.0}, Nation.US, new double[]{8.0, 14.0, 22.0})),
            Map.entry("친환경 및 재생 에너지", Map.of(Nation.KR, new double[]{10.0, 22.0, 40.0}, Nation.US, new double[]{16.0, 30.0, 55.0})),

            // 건설 및 유틸리티
            Map.entry("건설 및 엔지니어링", Map.of(Nation.KR, new double[]{5.0, 10.0, 16.0}, Nation.US, new double[]{10.0, 18.0, 28.0})),
            Map.entry("건축자재 및 포장", Map.of(Nation.KR, new double[]{6.0, 11.0, 18.0}, Nation.US, new double[]{11.0, 19.0, 30.0})),
            Map.entry("유틸리티 (전력/수도 등)", Map.of(Nation.KR, new double[]{7.0, 12.0, 20.0}, Nation.US, new double[]{14.0, 22.0, 32.0})),
            Map.entry("통신 서비스", Map.of(Nation.KR, new double[]{8.0, 14.0, 22.0}, Nation.US, new double[]{12.0, 20.0, 30.0})),

            // 금융 및 기타
            Map.entry("은행 및 신용 서비스", Map.of(Nation.KR, new double[]{4.0, 7.0, 11.0}, Nation.US, new double[]{8.0, 13.0, 20.0})),
            Map.entry("보험", Map.of(Nation.KR, new double[]{5.0, 9.0, 14.0}, Nation.US, new double[]{9.0, 15.0, 24.0})),
            Map.entry("자본 시장 및 자산 운용", Map.of(Nation.KR, new double[]{6.0, 11.0, 18.0}, Nation.US, new double[]{10.0, 18.0, 28.0})),
            Map.entry("부동산 및 리츠(REITs)", Map.of(Nation.KR, new double[]{5.0, 10.0, 18.0}, Nation.US, new double[]{30.0, 50.0, 80.0})),
            Map.entry("SPAC 및 지주사", Map.of(Nation.KR, new double[]{5.0, 10.0, 16.0}, Nation.US, new double[]{10.0, 18.0, 28.0})),
            Map.entry("상업 및 전문 서비스", Map.of(Nation.KR, new double[]{8.0, 14.0, 22.0}, Nation.US, new double[]{14.0, 24.0, 38.0})),
            Map.entry("기타 (미분류)", Map.of(Nation.KR, new double[]{8.0, 14.0, 22.0}, Nation.US, new double[]{12.0, 22.0, 35.0}))
    );

    // ── PBR 밴드: [극저평가 상한, 저평가 상한, 적정 상한, 고평가 상한] ────────

    private static final Map<Nation, double[]> DEFAULT_PBR_BANDS = Map.of(
            Nation.KR, new double[]{0.5, 1.0, 2.0, 5.0},
            Nation.US, new double[]{1.0, 3.0, 7.0, 20.0}
    );

    private static final Map<String, Map<Nation, double[]>> SECTOR_PBR_BANDS = Map.ofEntries(
            Map.entry("반도체", Map.of(Nation.KR, new double[]{0.8, 1.5, 3.0, 6.0}, Nation.US, new double[]{2.0, 5.0, 12.0, 25.0})),
            Map.entry("소프트웨어 및 IT 서비스", Map.of(Nation.KR, new double[]{1.5, 3.0, 6.0, 12.0}, Nation.US, new double[]{4.0, 10.0, 22.0, 45.0})),
            Map.entry("전자상거래 및 플랫폼 소매", Map.of(Nation.KR, new double[]{1.0, 2.5, 5.0, 10.0}, Nation.US, new double[]{3.0, 8.0, 18.0, 40.0})),
            Map.entry("제약 및 생명공학", Map.of(Nation.KR, new double[]{1.0, 2.5, 5.0, 10.0}, Nation.US, new double[]{2.0, 5.0, 12.0, 28.0})),
            Map.entry("은행 및 신용 서비스", Map.of(Nation.KR, new double[]{0.3, 0.6, 1.0, 1.6}, Nation.US, new double[]{0.6, 1.0, 1.5, 2.5})),
            Map.entry("보험", Map.of(Nation.KR, new double[]{0.4, 0.7, 1.2, 2.0}, Nation.US, new double[]{0.8, 1.3, 2.0, 3.5})),
            Map.entry("부동산 및 리츠(REITs)", Map.of(Nation.KR, new double[]{0.5, 0.8, 1.2, 1.8}, Nation.US, new double[]{0.8, 1.2, 2.0, 3.5})),
            Map.entry("SPAC 및 지주사", Map.of(Nation.KR, new double[]{0.3, 0.7, 1.2, 2.0}, Nation.US, new double[]{0.8, 1.5, 3.0, 6.0})),
            Map.entry("전통 화석 에너지", Map.of(Nation.KR, new double[]{0.3, 0.7, 1.2, 2.0}, Nation.US, new double[]{0.8, 1.5, 3.0, 6.0})),
            Map.entry("철강 및 산업용 금속", Map.of(Nation.KR, new double[]{0.3, 0.6, 1.0, 1.8}, Nation.US, new double[]{0.7, 1.3, 2.5, 5.0})),
            Map.entry("친환경 및 재생 에너지", Map.of(Nation.KR, new double[]{0.7, 1.3, 2.5, 5.0}, Nation.US, new double[]{1.2, 3.0, 7.0, 15.0})),
            Map.entry("우주항공 및 국방", Map.of(Nation.KR, new double[]{0.8, 1.5, 3.0, 6.0}, Nation.US, new double[]{2.5, 5.0, 10.0, 20.0}))
            // 나머지는 DEFAULT_PBR_BANDS를 따르도록 유연하게 구성됨
    );

    private static final Set<String> ETF_SECTORS = Set.of(
            "테마·섹터형 ETF",
            "채권·현금성 ETF",
            "순수 지수형 ETF"
    );

    // ── public API ────────────────────────────────────────────────────────────

    public double[] getPerBand(String sector, Nation nation) {
        if (sector != null) {
            Map<Nation, double[]> byNation = SECTOR_PER_BANDS.get(sector);
            if (byNation != null) {
                return byNation.getOrDefault(nation, byNation.getOrDefault(Nation.KR, DEFAULT_PER_BANDS.get(Nation.KR)));
            }
        }
        return DEFAULT_PER_BANDS.getOrDefault(nation, DEFAULT_PER_BANDS.get(Nation.KR));
    }

    public double[] getPbrBand(String sector, Nation nation) {
        if (sector != null) {
            Map<Nation, double[]> byNation = SECTOR_PBR_BANDS.get(sector);
            if (byNation != null) {
                return byNation.getOrDefault(nation, byNation.getOrDefault(Nation.KR, DEFAULT_PBR_BANDS.get(Nation.KR)));
            }
        }
        return DEFAULT_PBR_BANDS.getOrDefault(nation, DEFAULT_PBR_BANDS.get(Nation.KR));
    }

    public boolean isFinanceSector(String sector) {
        if (sector == null) return false;
        return sector.equals("은행 및 신용 서비스")
                || sector.equals("보험")
                || sector.equals("자본 시장 및 자산 운용")
                || sector.equals("부동산 및 리츠(REITs)")
                || sector.equals("SPAC 및 지주사");
    }

    public boolean isEtfSector(String sector) {
        return sector != null && ETF_SECTORS.contains(sector);
    }
}