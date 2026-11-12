package com.stocklog.modules.ai;

import com.stocklog.modules.market.analysis.dto.EngineInput;
import com.stocklog.modules.market.analysis.diagnosis.MarketDiagnosisHistoryDto;
import com.stocklog.modules.market.news.dto.NewsDto;
import com.stocklog.modules.purchase.portfolio.analysis.dto.PortfolioAnalysisResult;
import com.stocklog.modules.stock.analysis.result.StockAnalysisResult;

import java.util.List;


public interface AiProvider {

    /** AI 호출 실패 시 돌려주는 문구 */
    String ERROR_MESSAGE = "AI 분석 중 오류 발생";

    /** 시장 진단처럼 문자열로 저장하는 곳에서 'AI 사용량 소진'을 표시하는 문구 */
    String QUOTA_MESSAGE = "AI 사용량 소진";

    /** 종목 최근 뉴스 요약 (한국어). news는 화면에 보이는 순서 그대로 [1]부터 번호가 붙는다 */
    String getNewsSummary(String code, List<NewsDto> news);

    /** 종목 AI 리포트 (JSON 문자열) */
    String getStockReport(StockAnalysisResult analysis, String context);

    /** 포트폴리오 AI 주간 점검 (JSON 문자열). 실패하면 AiException */
    String getPortfolioReview(String facts, String context, String previous);

    String getStockAnalysis(StockAnalysisResult analysisResult);

    String getPortfolioAnalysis(PortfolioAnalysisResult analysisResult);

    /** 시장 시황 브리핑 (JSON 문자열). rules: 앱 규칙과 이번 주 사실 */
    String getMarketAnalysis(EngineInput input, List<NewsDto> newsList, MarketDiagnosisHistoryDto historyDto, String rules);

    /** 오늘 뉴스의 섹터 영향 (JSON 문자열) */
    String getNewsSectorImpact(String news, String sectors);
}