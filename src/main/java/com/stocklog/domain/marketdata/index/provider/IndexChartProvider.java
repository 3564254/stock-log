package com.stocklog.domain.marketdata.index.provider;

import com.stocklog.domain.marketdata.index.dto.IndexResponse;
import com.stocklog.domain.marketdata.index.type.IndexType;

public interface IndexChartProvider {
    IndexResponse fetchIndexResponse(IndexType type);
}
