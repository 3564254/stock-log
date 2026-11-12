package com.stocklog.application.dashboard.usecase;

import com.stocklog.application.dashboard.MainDashboardResponse;

/** 메인 화면에 필요한 데이터를 조합하는 use-case 경계 */
public interface MainDashboardUseCase {
    MainDashboardResponse getDashboardData(Long memberId);
}
