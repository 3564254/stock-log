package com.stocklog.application.dashboard.usecase;

import com.stocklog.application.dashboard.MainDashboardResponse;

/**
 * 메인 화면에 필요한 데이터를 조합하는 use-case 경계.
 *
 * TODO(구조): MainController의 포트폴리오 분석·섹터 집계·관심 종목 조합 책임을 옮길 후보이다.
 */
public interface MainDashboardUseCase {
    MainDashboardResponse getDashboardData(Long memberId);
}
