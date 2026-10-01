package com.stocklog.modules.stock.chart;

import com.stocklog.modules.stock.chart.id.StockChartId;
import org.springframework.data.jpa.repository.JpaRepository;
import com.stocklog.modules.common.type.Period;

import java.util.List;

public interface StockChartRepository extends JpaRepository<StockChart, StockChartId> {

    // (참고) 특정 종목의 차트 데이터를 최신순으로 정렬해서 가져오고 싶을 때 많이 쓰는 메서드
    List<StockChart> findByCodeAndPeriodOrderByTradeDateDesc(String code, Period period);

    // 데이터가 과거부터 최신순으로 적재됨 (인덱스 0 = 100일 전, 마지막 인덱스 = 오늘)
    List<StockChart> findByCodeAndPeriodOrderByTradeDateAsc(String code, Period period);
}
