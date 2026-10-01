package com.stocklog.modules.market.analysis.service.result;

public enum ScoreCategory {
    STRONG_POSITIVE, // +80 ~ +100
    POSITIVE,        // +40 ~ +79
    NEUTRAL,         // -39 ~ +39
    NEGATIVE,        // -79 ~ -40
    STRONG_NEGATIVE  // -100 ~ -80
}