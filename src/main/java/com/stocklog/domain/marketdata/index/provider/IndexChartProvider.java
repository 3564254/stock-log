package com.stocklog.domain.marketdata.index.provider;

import com.stocklog.domain.marketdata.index.dto.IndexDto;
import com.stocklog.domain.marketdata.index.type.IndexType;

public interface IndexChartProvider {
    IndexDto fetchIndexResponse(IndexType type);
}
