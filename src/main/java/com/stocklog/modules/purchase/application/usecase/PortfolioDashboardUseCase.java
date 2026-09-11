package com.stocklog.modules.purchase.application.usecase;

import com.stocklog.modules.purchase.application.PortfolioDashboardResponse;

/**
 * 포트폴리오 화면/API에 필요한 포트폴리오, 관심 종목, 매매 규칙을 조합하는 use-case 경계.
 */
public interface PortfolioDashboardUseCase {
    PortfolioDashboardResponse getDashboardData(Long memberId);
}
