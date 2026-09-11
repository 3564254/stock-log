package com.stocklog.modules.purchase.trade.dto.response;

import com.stocklog.modules.purchase.trade.dto.vo.WatchItemDto;
import lombok.Builder;

import java.util.List;

@Builder
public record WatchListResponse(
        Long memberId,
        List<WatchItemDto> itemResponseList
) {
}

