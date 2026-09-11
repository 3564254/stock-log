package com.stocklog.modules.market.index.type;
import com.stocklog.modules.stock.domain.type.Period;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum IndexType {

    // ── 0. 글로벌 환율 / 달러 (리스크온-오프, 캐리트레이드 판단용) ──
    USD_JPY("DEXJPUS", "엔/달러 환율 (엔 캐리 트레이드 모니터링)", Period.DAY),
    DOLLAR_INDEX("DTWEXBGS", "무역가중 달러 인덱스", Period.DAY),

    // ── 1. 미국 전체 시장 대표 지수 ──
    SP500("SP500", "S&P 500", Period.DAY),
    NASDAQ_100("NASDAQ100", "나스닥 100 지수", Period.DAY),
    DOW_JONES("DJIA", "다우존스 산업평균지수", Period.DAY),

    // ── 2. 시장 변동성 및 신용 리스크 ──
    VIX("VIXCLS", "VIX (변동성 지수)", Period.DAY),
    HIGH_YIELD_SPREAD("BAMLH0A0HYM2", "하이일드 채권 스프레드 (신용 리스크)", Period.DAY),
    FINANCIAL_STRESS("STLFSI4", "세인트루이스 연은 금융스트레스지수", Period.WEEK),

    // ── 3. 채권/금리 (Fed 정책 및 경기 선행 신호) ──
    US_TREASURY_10Y("DGS10", "미국 10년물 국채 금리", Period.DAY),   // Period 오류 수정 (DGS 계열은 일별)
    US_TREASURY_2Y("DGS2", "미국 2년물 국채 금리", Period.DAY),      // Period 오류 수정
    T10Y2Y("T10Y2Y", "10년물-2년물 국채 금리차 (장단기 역전 여부)", Period.DAY), // Period 오류 수정
    US_FED_FUNDS_RATE("FEDFUNDS", "미국 연방기금금리 (기준금리)", Period.MONTH),

    // ── 4. 원자재 / 대체자산 ──
    WTI_CRUDE_OIL("DCOILWTICO", "WTI 원유", Period.DAY),
    BITCOIN("CBBTCUSD", "비트코인 (리스크자산 심리 참고)", Period.DAY),

    // ── 5. 거시 물가 ──
    US_CPI("CPIAUCSL", "미국 소비자물가지수 (CPI, 시장 headline 반응 큼)", Period.MONTH),
    US_CORE_PCE("PCEPILFE", "미국 근원 PCE (Fed 공식 타겟 지표)", Period.MONTH),

    // ── 6. 고용 ──
    US_UNEMPLOYMENT_RATE("UNRATE", "미국 실업률", Period.MONTH),
    US_NONFARM_PAYROLLS("PAYEMS", "미국 비농업 고용자 수 (NFP)", Period.MONTH),
    US_INITIAL_CLAIMS("ICSA", "미국 신규 실업수당 청구건수 (주간 선행지표)", Period.WEEK),

    // ── 7. 경기 및 소비 ──
    US_GDP("GDP", "미국 명목 GDP", Period.QUARTER),
    US_RETAIL_SALES("RSAFS", "미국 소매판매", Period.MONTH),
    US_CONSUMER_SENTIMENT("UMCSENT", "미시간대 소비자심리지수", Period.MONTH),
    US_CONSUMER_REVOLVING_LOANS("CCLACBW027SBOG", "가계 신용카드/리볼빙 대출 잔고 (소비 여력)", Period.WEEK),

    // ── 8. 유동성 (Fed 대차대조표) ──
    FED_TOTAL_ASSETS("WALCL", "연준 총자산 (대차대조표)", Period.WEEK),
    REVERSE_REPO("RRPONTSYD", "미국 역레포 잔고 (ON RRP)", Period.DAY),
    US_M2("M2SL", "미국 M2 통화량", Period.MONTH),

    // ── 9. 반도체/AI 핵심 (사용자 주력 관심 섹터) ──
    PHLX_SEMI("NASDAQSOX", "필라델피아 반도체 지수 (SOX)", Period.DAY),
    NASDAQ_AI_BIGDATA("NASDAQNYGBIG", "나스닥 글로벌 AI & 빅데이터 지수", Period.DAY),
    NASDAQ_CAPEX_ACHIEVERS("NASDAQCAPEXA", "나스닥 미국 CapEx 우수기업 지수 (AI 설비투자 사이클)", Period.DAY),
    IND_SEMI("IPG3344S", "반도체 및 전자부품 생산지수 (실물경기 확인용)", Period.MONTH),

    // ── 10. 기타 섹터 (수급/가격 기준, 넓게 커버) ──
    NASDAQ_ISE_CYBERSECURITY("NASDAQHXR", "ISE 사이버 시큐리티 지수 (HACK 벤치마크)", Period.DAY),
    NASDAQ_BIOTECH("NASDAQNBI", "나스닥 바이오테크놀로지 지수", Period.DAY),
    DOW_JONES_UTILITY("DJU", "다우존스 유틸리티 평균지수", Period.DAY),
    NASDAQ_CONSUMER_STAPLES("NASDAQNQUSB45", "나스닥 미국 필수소비재 지수", Period.DAY),
    NASDAQ_CLEAN_ENERGY("NASDAQCELS", "나스닥 클린 엣지 그린 에너지 지수", Period.DAY),
    PHLX_GOLD_SILVER("NASDAQXAU", "필라델피아 금·은 광업 지수 (XAU)", Period.DAY),
    SECTOR_DISCRETIONARY_DURABLE("IPDCONGD", "경기소비재 (내구 소비재 생산지수)", Period.MONTH),
    SECTOR_FINANCIALS("TOTCI", "금융 (상업/산업 대출 총액)", Period.WEEK),
    SECTOR_REAL_ESTATE("CSUSHPISA", "부동산 (케이스-실러 주택가격지수)", Period.MONTH),
    SECTOR_INDUSTRIALS("IPG333S", "산업재 (기계류 제조업 생산지수)", Period.MONTH),
    US_INDPRO("INDPRO", "미국 전체 산업생산지수 (경기 총괄)", Period.MONTH);



    private final String seriesId;
    private final String displayName;
    private final Period frequency;

    public static IndexType fromSeriesId(String seriesId) {
        for (IndexType type : values()) {
            if (type.getSeriesId().equalsIgnoreCase(seriesId)) {
                return type;
            }
        }
        throw new IllegalArgumentException("지원하지 않는 지수 ID 입니다: " + seriesId);
    }
}