package com.stocklog.modules.market.news.dto;

import com.stocklog.modules.market.news.News;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record NewsDto(
        String category,
        LocalDateTime dateTime,
        String headline,
        String newsId,
        String image,
        String related,
        String source,
        String summary,
        String url
) {
    public static NewsDto createFallback() {
        return NewsDto.builder()
                .related("")
                .category("")
                .dateTime(null)
                .headline("")
                .newsId("")
                .image("")
                .source("")
                .summary("")
                .url("")
                .build();
    }

    public static News toEntity(NewsDto newsDto) {
        return News.builder()
                .newsId(newsDto.newsId())
                .related(newsDto.related())
                .category(newsDto.category())
                .dateTime(newsDto.dateTime())
                .headline(newsDto.headline())
                .image(newsDto.image())
                .source(newsDto.source())
                .summary(newsDto.summary())
                .url(newsDto.url())
                .build();
    }

    public static NewsDto from(News news) {
        return NewsDto.builder()
                .category(news.getCategory())
                .dateTime(news.getDateTime())
                .headline(news.getHeadline())
                .newsId(news.getNewsId())
                .image(news.getImage())
                .related(news.getRelated())
                .source(news.getSource())
                .summary(news.getSummary())
                .url(news.getUrl())
                .build();
    }
}
