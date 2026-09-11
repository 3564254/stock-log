package com.stocklog.modules.market.analysis.dto;


public record MarketAnalysisResultResponse(
        int fearAndGreedScore,
        String marketSentiment,
        String verdict

) {
}
