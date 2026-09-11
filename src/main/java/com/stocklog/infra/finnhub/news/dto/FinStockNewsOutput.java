package com.stocklog.infra.finnhub.news.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class FinStockNewsOutput {

    @JsonProperty("category")
    private String category;

    @JsonProperty("datetime")
    private Long unixDateTime;

    @JsonProperty("headline")
    private String headLine;

    @JsonProperty("id")
    private Long newsId;

    @JsonProperty("image")
    private String image;

    @JsonProperty("related")
    private String related;

    @JsonProperty("source")
    private String source;

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("url")
    private String url;


}
