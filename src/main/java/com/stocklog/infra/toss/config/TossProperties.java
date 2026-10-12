package com.stocklog.infra.toss.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml
 *   toss:
 *     api-key: ${TOSS_API_KEY}
 *     secret-key: ${TOSS_SECRET_KEY}
 * 키 값은 yml에 직접 쓰지 말고 환경 변수로 넣는다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "toss")
public class TossProperties {
    private String apiKey;
    private String secretKey;
}
