package com.stocklog.modules.post.entity;

import com.stocklog.modules.stock.stock.Stock;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게시글 ↔ 종목 태그 (해시태그 역할).
 * - uk_post_stock (post_id, stock_code): 한 글에 같은 종목 중복 금지 + "글 → 태그 목록" 조회 인덱스
 * - idx_stock_code_post_id (stock_code, post_id): "종목 → 글 목록(최신순, 커서)" 조회 인덱스
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "post_stock_tag",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_post_stock", columnNames = {"post_id", "stock_code"})
        },
        indexes = {
                @Index(name = "idx_stock_code_post_id", columnList = "stock_code, post_id")
        }
)
public class PostStockTag {

    /** 한 글에 붙일 수 있는 종목 수 */
    public static final int MAX_PER_POST = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_code", nullable = false)
    private Stock stock;

    public PostStockTag(Post post, Stock stock) {
        this.post = post;
        this.stock = stock;
    }
}
