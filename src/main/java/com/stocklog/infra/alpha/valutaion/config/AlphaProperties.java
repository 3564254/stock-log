package com.stocklog.infra.alpha.valutaion.config;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "alpha")
public class AlphaProperties {
    String apiKey;
}
