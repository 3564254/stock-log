package com.stocklog.infra.fred.config;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "fred")
public class FredProperties {
    String apiKey;
}
