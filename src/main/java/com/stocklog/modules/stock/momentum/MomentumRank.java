package com.stocklog.modules.stock.momentum;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** 미국 S&P500 종목의 12-1개월 모멘텀 순위 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "momentum_rank", indexes = {
        @Index(name = "idx_momentum_asof", columnList = "as_of"),
        @Index(name = "idx_momentum_code", columnList = "code")
})
public class MomentumRank {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(length = 100)
    private String name;

    @Column(length = 60)
    private String sector;

    @Column(name = "as_of", nullable = false)
    private LocalDate asOf;

    @Column(name = "ret_12_1", nullable = false)
    private double ret121;

    @Column(name = "rank_no", nullable = false)
    private int rankNo;

    @Column(nullable = false)
    private double pct;

    @Column(name = "universe_size", nullable = false)
    private int universeSize;

    public MomentumRank(String code, String name, String sector, LocalDate asOf, double ret121, int rankNo, double pct, int universeSize) {
        this.code = code; this.name = name; this.sector = sector; this.asOf = asOf;
        this.ret121 = ret121; this.rankNo = rankNo; this.pct = pct; this.universeSize = universeSize;
    }
}
