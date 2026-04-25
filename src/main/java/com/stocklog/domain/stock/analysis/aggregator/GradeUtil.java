package com.stocklog.domain.stock.analysis.aggregator;

// GradeUtil.java
public class GradeUtil {
    public static String toGrade(int score) {
        if (score >= 80) return "S";
        if (score >= 65) return "A";
        if (score >= 48) return "B";
        if (score >= 30) return "C";
        return "D";
    }
}
