package com.stocklog.modules.market.news;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_news")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class News {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "news_id", unique = true, nullable = false)
    private String newsId;

    @Column(name = "category")
    private String category;

    @Column(name = "headline", nullable = false, length = 500)
    private String headline;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "source", length = 100)
    private String source;

    @Column(name = "related", length = 100)
    private String related;

    @Column(name = "url", length = 1000)
    private String url;

    @Column(name = "image_url", length = 1000)
    private String image;

    @Column(name = "date_time")
    private LocalDateTime dateTime;     // KST 기준

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 20)
    private NewsSource provider;


    @Builder

    public News(Long id, String newsId, String category, String headline, String summary, String source, String related, String url, String image, LocalDateTime dateTime, NewsSource provider) {
        this.id = id;
        this.newsId = newsId;
        this.category = category;
        this.headline = headline;
        this.summary = summary;
        this.source = source;
        this.related = related;
        this.url = url;
        this.image = image;
        this.dateTime = dateTime;
        this.provider = provider;
    }
}
