package com.stocklog.modules.market.index.dto;

import lombok.Builder;

@Builder
public record IndexDashboardDto(
        // 글로벌 환율 / 달러
        IndexDto usdJpy,
        IndexDto dollarIndex,

        // 미국 전체 시장 대표 지수
        IndexDto sp500,
        IndexDto nasdaq100,
        IndexDto dowJones,

        // 시장 변동성 및 신용 리스크
        IndexDto vix,
        IndexDto highYieldSpread,
        IndexDto financialStressIndex,

        // 채권 및 금리
        IndexDto us10y,
        IndexDto us2y,
        IndexDto yieldCurve10y2y,
        IndexDto fedFundsRate,

        // 원자재 / 대체자산
        IndexDto wti,
        IndexDto bitcoin,

        // 거시 물가
        IndexDto cpi,
        IndexDto corePce,

        // 고용
        IndexDto unemploymentRate,
        IndexDto nonfarmPayrolls,
        IndexDto initialClaims,

        // 경기 및 소비
        IndexDto gdp,
        IndexDto retailSales,
        IndexDto consumerSentiment,
        IndexDto consumerRevolvingLoans,

        // 유동성
        IndexDto fedTotalAssets,
        IndexDto reverseRepo,
        IndexDto m2,

        // 반도체/AI 핵심
        IndexDto phlxSemi,
        IndexDto nasdaqAiBigdata,
        IndexDto nasdaqCapexAchievers,
        IndexDto indSemi,

        // 기타 섹터
        IndexDto nasdaqIseCybersecurity,
        IndexDto nasdaqBiotech,
        // IndexDto dowJonesUtility
        IndexDto nasdaqConsumerStaples,
        IndexDto nasdaqCleanEnergy,
        IndexDto phlxGoldSilver,
        IndexDto sectorDiscretionaryDurable,
        IndexDto sectorFinancials,
        IndexDto sectorRealEstate,
        IndexDto sectorIndustrials,
        IndexDto usIndpro
){
}