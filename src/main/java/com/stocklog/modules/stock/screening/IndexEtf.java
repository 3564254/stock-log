package com.stocklog.modules.stock.screening;

import com.stocklog.modules.common.type.Nation;

import java.util.List;

/** 시장 전체 흐름을 보려고 따로 분석하는 지수 ETF. 시가총액 순위와 상관없이 항상 분석한다 */
public record IndexEtf(Nation nation, String code, String label) {

    public static final List<IndexEtf> ALL = List.of(
            new IndexEtf(Nation.US, "QQQ", "나스닥100"),
            new IndexEtf(Nation.US, "SPY", "S&P500"),
            new IndexEtf(Nation.US, "DIA", "다우30"),
            new IndexEtf(Nation.US, "IWM", "러셀2000"),
            new IndexEtf(Nation.US, "SOXX", "반도체"),
            new IndexEtf(Nation.US, "SCHD", "배당성장"),
            new IndexEtf(Nation.KR, "069500", "코스피200"),
            new IndexEtf(Nation.KR, "229200", "코스닥150")
    );

    public static List<IndexEtf> of(Nation nation) {
        return ALL.stream().filter(e -> e.nation() == nation).toList();
    }

    /** 화면 정렬용: 위 목록 순서 */
    public static int orderOf(String code) {
        for (int i = 0; i < ALL.size(); i++) {
            if (ALL.get(i).code().equals(code)) return i;
        }
        return Integer.MAX_VALUE;
    }
}
