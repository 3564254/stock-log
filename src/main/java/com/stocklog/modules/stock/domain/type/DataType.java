package com.stocklog.modules.stock.domain.type;

public enum DataType {
    CHART,INFO,PRICE,FINANCIAL,VALUTATION,AI,UNKNOWN;

    public static String getType(String name) {
        name = name.toLowerCase();
        if (name.contains("chart")) {
            return "Chart";
        } else if (name.contains("info")) {
            return "Info";
        } else if (name.contains("price")) {
            return "Price";
        } else if (name.contains("financial")) {
            return "Financial";
        } else if (name.contains("valuation")) {
            return "Valuation";
        } else if (name.contains("ai")) {
            return "Ai";
        } else {
            return "Unknown";
        }
    }
}
