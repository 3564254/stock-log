package com.stocklog.modules.stock.briefing;

import java.util.List;

/** 회사 일정(실적 발표) 제공처. 해외(미국) 종목만 지원한다 */
public interface CompanyEventProvider {

    /** 오늘부터 앞으로 days일 안의 실적 발표 일정 (없으면 빈 목록) */
    List<CompanyBriefing.Earnings> upcomingEarnings(String symbol, int days);

    /** 최근 분기 실적 (실제 EPS vs 예상), 최신순 (없으면 빈 목록) */
    List<CompanyBriefing.PastEarnings> recentEarnings(String symbol);

    /** 애널리스트 의견 월별 집계, 최신순 (없거나 실패하면 빈 목록) */
    List<CompanyBriefing.Analysts> recommendations(String symbol);

    /** 회사 프로필의 로고 URL. 없으면 "" (없는 종목), 조회 실패(한도 초과 등)면 null */
    String logoUrl(String symbol);
}
