package com.stocklog.modules.market.index.provider;

import com.stocklog.modules.market.index.dto.IndexDto;
import com.stocklog.modules.market.domain.type.IndexType;

public interface IndexChartProvider {
    IndexDto fetchIndexResponse(IndexType type);
}
