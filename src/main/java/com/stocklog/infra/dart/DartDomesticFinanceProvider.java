package com.stocklog.infra.dart;

import com.stocklog.modules.stock.domain.type.MarketType;
import com.stocklog.modules.stock.financial.StockFinanceProvider;
import com.stocklog.modules.stock.financial.dto.internal.StockFinancialDto;
import com.stocklog.modules.stock.financial.dto.response.StockEarningResponse;
import com.stocklog.modules.stock.financial.dto.response.StockFinancialsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 국내 종목 재무 지표 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DartDomesticFinanceProvider implements StockFinanceProvider {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final DartClient dart;

    private record Report(int year, String code, int months, String label) {}

    @Override
    public StockFinancialsResponse fetchFinancial(String symbol) {
        String corp = dart.corpCode(symbol);
        if (corp == null) {
            log.info("[DART] 고유번호를 찾지 못함 (ETF · 우선주 · 키 없음 등) - 종목코드: {}", symbol);
            return StockFinancialsResponse.createFallback();
        }
        for (Report r : candidates(LocalDate.now(KST))) {
            List<DartClient.Account> list = dart.singleAccounts(corp, r.year(), r.code());
            if (list == null) return StockFinancialsResponse.createFallback();   // 키 · 한도 · 네트워크 오류: 더 찾지 않는다
            if (list.isEmpty()) continue;                                          // 아직 공시 전 → 이전 보고서
            StockFinancialDto dto = compute(list, r);
            if (dto != null && !dto.isEmpty()) {
                return StockFinancialsResponse.builder().symbol(symbol).stockFinancialDto(dto).build();
            }
        }
        log.info("[DART] 최근 정기보고서 주요계정 없음 - 종목코드: {}", symbol);
        return StockFinancialsResponse.createFallback();
    }

    /** 오늘 기준으로 기간이 끝난 보고서를 최신순으로 (최대 5개) */
    static List<Report> candidates(LocalDate today) {
        List<Report> all = new ArrayList<>();
        for (int y = today.getYear(); y >= today.getYear() - 1; y--) {
            all.add(new Report(y, "11014", 9, y + "년 3분기보고서"));
            all.add(new Report(y, "11012", 6, y + "년 반기보고서"));
            all.add(new Report(y, "11013", 3, y + "년 1분기보고서"));
            all.add(new Report(y - 1, "11011", 12, (y - 1) + "년 사업보고서"));
        }
        List<Report> out = new ArrayList<>();
        for (Report r : all) {
            LocalDate end = LocalDate.of(r.year(), r.months() == 12 ? 12 : r.months(), 1).plusMonths(1).minusDays(1);
            if (!end.isBefore(today)) continue;
            if (out.stream().noneMatch(o -> o.year() == r.year() && o.code().equals(r.code()))) out.add(r);
            if (out.size() >= 5) break;
        }
        return out;
    }

    private StockFinancialDto compute(List<DartClient.Account> list, Report r) {
        boolean hasCfs = list.stream().anyMatch(a -> "CFS".equals(a.fsDiv()));
        String fs = hasCfs ? "CFS" : "OFS";
        Map<String, DartClient.Account> m = new HashMap<>();
        for (DartClient.Account a : list) {
            if (a.accountNm() == null || !fs.equals(a.fsDiv())) continue;
            m.putIfAbsent(norm(a.accountNm()), a);
        }
        DartClient.Account rev = first(m, "매출액", "영업수익", "수익");
        DartClient.Account op = first(m, "영업이익");
        DartClient.Account ni = first(m, "당기순이익");
        DartClient.Account assets = first(m, "자산총계");
        DartClient.Account liab = first(m, "부채총계");
        DartClient.Account equity = first(m, "자본총계");
        DartClient.Account ca = first(m, "유동자산");
        DartClient.Account cl = first(m, "유동부채");

        boolean quarterly = r.months() < 12;
        // 누적 금액끼리 비교 (분기 보고서). 누적이 없으면 3개월 금액끼리
        boolean cum = quarterly && hasAdd(rev) && hasAdd(ni);
        BigDecimal revNow = flow(rev, cum, false), revPrev = flow(rev, cum, true);
        BigDecimal opNow = flow(op, cum, false);
        BigDecimal niNow = flow(ni, cum, false), niPrev = flow(ni, cum, true);
        int months = !quarterly ? 12 : (cum ? r.months() : 3);

        BigDecimal eq = amt(equity == null ? null : equity.thstrmAmount());
        BigDecimal as = amt(assets == null ? null : assets.thstrmAmount());
        BigDecimal li = amt(liab == null ? null : liab.thstrmAmount());
        BigDecimal cav = amt(ca == null ? null : ca.thstrmAmount());
        BigDecimal clv = amt(cl == null ? null : cl.thstrmAmount());

        BigDecimal niYear = niNow == null ? null : niNow.multiply(BigDecimal.valueOf(12)).divide(BigDecimal.valueOf(months), 4, RoundingMode.HALF_UP);

        return StockFinancialDto.builder()
                .operatingMargin(pct(opNow, revNow))
                .netMargin(pct(niNow, revNow))
                .roe(positive(eq) ? pct(niYear, eq) : null)
                .roa(positive(as) ? pct(niYear, as) : null)
                .revenueGrowth(growth(revNow, revPrev))
                .epsGrowth(growth(niNow, niPrev))
                .debtToEquity(positive(eq) && li != null ? li.divide(eq, 4, RoundingMode.HALF_UP) : null)
                .currentRatio(positive(clv) && cav != null ? cav.divide(clv, 4, RoundingMode.HALF_UP) : null)
                .source("DART " + r.label() + " · " + (hasCfs ? "연결" : "별도") + (quarterly ? (cum ? " · 누적" : " · 3개월") : ""))
                .build();
    }

    private static boolean hasAdd(DartClient.Account a) {
        return a != null && amt(a.thstrmAddAmount()) != null && amt(a.frmtrmAddAmount()) != null;
    }

    private static BigDecimal flow(DartClient.Account a, boolean cum, boolean prev) {
        if (a == null) return null;
        if (cum) return amt(prev ? a.frmtrmAddAmount() : a.thstrmAddAmount());
        return amt(prev ? a.frmtrmAmount() : a.thstrmAmount());
    }

    private static DartClient.Account first(Map<String, DartClient.Account> m, String... names) {
        for (String n : names) { DartClient.Account a = m.get(n); if (a != null) return a; }
        return null;
    }

    /** 공백 · 괄호 내용 제거: '당기순이익(손실)' → '당기순이익' */
    private static String norm(String s) { return s.replaceAll("\\(.*?\\)", "").replaceAll("\\s", ""); }

    static BigDecimal amt(String s) {
        if (s == null) return null;
        String t = s.trim().replace(",", "");
        if (t.isEmpty() || "-".equals(t)) return null;
        try { return new BigDecimal(t); } catch (NumberFormatException e) { return null; }
    }

    private static boolean positive(BigDecimal v) { return v != null && v.signum() > 0; }

    private static BigDecimal pct(BigDecimal a, BigDecimal b) {
        if (a == null || !positive(b)) return null;
        return a.multiply(BigDecimal.valueOf(100)).divide(b, 2, RoundingMode.HALF_UP);
    }

    /** 작년이 0 이하(적자)면 성장률이 의미 없어 null */
    private static BigDecimal growth(BigDecimal now, BigDecimal prev) {
        if (now == null || !positive(prev)) return null;
        return now.subtract(prev).multiply(BigDecimal.valueOf(100)).divide(prev, 2, RoundingMode.HALF_UP);
    }

    @Override
    public StockEarningResponse fetchEarnings(String symbol) { return null; }

    @Override
    public boolean supports(MarketType marketType) { return marketType.isDomestic(); }
}
