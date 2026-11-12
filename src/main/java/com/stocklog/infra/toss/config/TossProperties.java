package com.stocklog.infra.toss.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml */
@Getter
@Setter
@ConfigurationProperties(prefix = "toss")
public class TossProperties {
    private String apiKey;
    private String secretKey;
}
