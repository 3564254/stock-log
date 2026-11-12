package com.stocklog.modules.stock.logo;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 종목 로고 URL (Finnhub 프로필). 로고는 거의 안 바뀌므로 한 번 받아 두고 계속 쓴다 */
@Entity
@Table(name = "stock_logo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockLogo {

    @Id
    @Column(name = "stock_code", length = 20)
    private String code;

    @Column(name = "url", length = 500)
    private String url;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    public StockLogo(String code, String url, LocalDateTime checkedAt) {
        this.code = code;
        this.url = url;
        this.checkedAt = checkedAt;
    }

    public void update(String url, LocalDateTime checkedAt) {
        this.url = url;
        this.checkedAt = checkedAt;
    }
}
