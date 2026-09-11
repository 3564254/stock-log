package com.stocklog.modules.market.news.dto;

import com.stocklog.modules.market.news.NewsType;
import com.stocklog.modules.stock.stock.dto.StockDto;
import groovyjarjarantlr4.v4.runtime.misc.Nullable;
import lombok.Builder;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Objects;

@Builder
public record NewsResponse(
        @Nullable StockDto stock,
        List<NewsDto> newsList,
        NewsType newsType,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static NewsResponse createStockNews(StockDto stock, Page<NewsDto> newsPage) {
        Objects.requireNonNull(stock, "주식 뉴스에는 StockDto가 필수입니다.");
        return NewsResponse.builder()
                .stock(stock)
                .newsList(newsPage.getContent())
                .newsType(NewsType.STOCK)
                .page(newsPage.getNumber())
                .size(newsPage.getSize())
                .totalElements(newsPage.getTotalElements())
                .totalPages(newsPage.getTotalPages())
                .hasNext(newsPage.hasNext())
                .build();
    }

    public static NewsResponse createMarketNews(Page<NewsDto> newsPage) {
        return NewsResponse.builder()
                .stock(null)
                .newsList(newsPage.getContent())
                .newsType(NewsType.MARKET)
                .page(newsPage.getNumber())
                .size(newsPage.getSize())
                .totalElements(newsPage.getTotalElements())
                .totalPages(newsPage.getTotalPages())
                .hasNext(newsPage.hasNext())
                .build();
    }
}