package com.stocklog.modules.market.analysis.service.analyzer;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketTrendAnalyzer {

    // 이동평균
    public record MovingAverage(int ma20, int ma50, int ma200){};
    // 이격도
    public record Disparity(int disparity20, int disparity50, int disparity200){};
    // 이동평균선 기울기
    public record Slope(int ma20Slope, int ma50Slope, int ma200Slope) {};
    // 기간별 수익률
    public record Return(int return20, int return50, int return200){};
    // 드로다운: 최고점 대비 현재 얼마나 하락했는지를 나타내는 위험 지표
    public record Drawdown(int drawdown){};
    // 52주 최고가에 얼마나 근접해 있는지를 나타내는 상방 돌파 가능성 지표
    public record High52wProximity(int high52wProximity){};

}
