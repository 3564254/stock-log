package com.stocklog.modules.stock.screening;

import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.common.finance.indicator.phase.PhaseIndicators;
import com.stocklog.modules.common.finance.model.ExchangeSession;
import com.stocklog.modules.common.type.ChartDataType;
import com.stocklog.modules.common.type.ChartPhase;
import com.stocklog.modules.common.type.Nation;
import com.stocklog.modules.stock.analysis.analyzer.MomentumAnalyzer;
import com.stocklog.modules.stock.analysis.analyzer.TechnicalAnalyzer;
import com.stocklog.modules.stock.analysis.score.MomentumScore;
import com.stocklog.modules.stock.analysis.score.TechnicalScore;
import com.stocklog.modules.stock.analysis.signal.trading.EntryPlanner;
import com.stocklog.modules.stock.analysis.signal.trading.EntryType;
import com.stocklog.modules.stock.analysis.signal.trading.TradingSignalAnalyzer;
import com.stocklog.modules.stock.analysis.signal.trading.TradingSignalResult;
import com.stocklog.modules.stock.analysis.signal.trading.Verdict;
import com.stocklog.modules.stock.chart.StockChartService;
import com.stocklog.modules.stock.chart.dto.StockChartDto;
import com.stocklog.modules.stock.momentum.MomentumRank;
import com.stocklog.modules.stock.momentum.MomentumRankRepository;
import com.stocklog.modules.stock.momentum.MomentumService;
import com.stocklog.modules.stock.price.StockPriceService;
import com.stocklog.modules.stock.price.dto.StockPriceDto;
import com.stocklog.modules.stock.screening.dto.ScreeningCandidate;
import com.stocklog.modules.stock.screening.dto.ScreeningResponse;
import com.stocklog.modules.stock.screening.entity.ScreeningCategory;
import com.stocklog.modules.stock.screening.entity.ScreeningResult;
import com.stocklog.modules.stock.screening.entity.ScreeningStage;
import com.stocklog.modules.stock.signal.SignalRecord;
import com.stocklog.modules.stock.signal.SignalRecorder;
import com.stocklog.modules.stock.stock.Stock;
import com.stocklog.modules.stock.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 지수 ETF(고정 목록)와 시가총액 상위 종목을 차트 기준으로 분석해 두고, 홈 화면에 진입 후보를 보여 준다 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScreeningService {

    /** 진입 후보로 보여 줄 판정과 우선순위 (앞일수록 위) */
    private static final List<Verdict> CANDIDATE_VERDICTS =
            List.of(Verdict.ACTIONABLE, Verdict.WAIT_TRIGGER, Verdict.BOUNDARY_WATCH, Verdict.WAIT_FOR_ZONE);

    /** "타점 준비 중": 진입 판정은 없지만 곧 타점이 나올 수 있는 국면 (앞일수록 위) */
    private static final List<ChartPhase> WATCHING_PHASES = List.of(
            ChartPhase.ENTRY_READY, ChartPhase.RE_ENTRY, ChartPhase.SETUP,
            ChartPhase.COOLDOWN, ChartPhase.RECOVERY_WATCH);

    private static final long PAUSE_MS = 300;   // 종목 사이 간격 (KIS 호출 제한)
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MarketCapProvider marketCapProvider;
    private final StockRepository stockRepository;
    private final com.stocklog.modules.stock.signal.Rsi2Service rsi2Service;
    private final StockChartService chartService;
    private final StockPriceService priceService;
    private final MomentumAnalyzer momentumAnalyzer;
    private final TechnicalAnalyzer technicalAnalyzer;
    private final TradingSignalAnalyzer tradingSignalAnalyzer;
    private final ScreeningStore store;
    private final ScreeningResultRepository repository;
    private final SignalRecorder signalRecorder;   // 신호 기록(전향적 검증)
    private final MomentumRankRepository momentumRepository;   // 미국 12-1 모멘텀 순위

    // 분석 (스케줄러에서 호출)

    /** 시가총액 상위 size개를 분석해 저장한다 */
    public void run(Nation nation, int size) {
        long started = System.currentTimeMillis();
        List<Target> targets = new ArrayList<>(pickIndexEtfs(nation));
        int etfCount = targets.size();
        targets.addAll(pickStocks(nation, size));
        if (targets.size() == etfCount) {
            log.warn("[Screening] {} 시가총액 상위 종목을 못 가져옴 → 지수 ETF {}개만 분석", nation, etfCount);
        }
        if (targets.isEmpty()) {
            log.warn("[Screening] {} 분석 대상이 없음 → 이전 결과 유지", nation);
            return;
        }
        // 미국: 시가총액 상위에 없는 12-1 모멘텀 상위 20% 종목도 판정한다
        if (nation == Nation.US) {
            Set<String> have = targets.stream().map(Target::code).collect(Collectors.toSet());
            List<Target> extras = pickMomentumExtras(have);
            targets.addAll(extras);
            if (!extras.isEmpty()) log.info("[Screening] US 모멘텀 상위 20% 중 시가총액 상위 밖 {}종목 추가", extras.size());
        }

        LocalDateTime analyzedAt = LocalDateTime.now(KST);
        // 직전 분석의 진입 후보 → 이번에 처음 후보가 된 종목에 NEW 표시
        List<ScreeningResult> previous = repository.findAllByNation(nation);
        Set<String> previousCandidates = previous.stream()
                .filter(r -> CANDIDATE_VERDICTS.contains(r.getVerdict()))
                .map(ScreeningResult::getStockCode)
                .collect(Collectors.toSet());
        boolean markNew = !previous.isEmpty();

        List<ScreeningResult> results = new ArrayList<>();
        List<SignalRecord> records = new ArrayList<>();
        for (Target target : targets) {
            if (Thread.currentThread().isInterrupted()) {
                log.info("[Screening] {} 분석 중단 (서버 종료)", nation);
                return;
            }
            try {
                results.add(analyze(nation, target, analyzedAt, markNew ? previousCandidates : null, records));
            } catch (Exception e) {
                log.warn("[Screening] {} {} 분석 실패: {}", nation, target.code(), e.toString());
            }
            pause();
        }

        if (results.isEmpty()) {
            log.warn("[Screening] {} {}개 모두 분석 실패 → 이전 결과 유지", nation, targets.size());
            return;
        }
        store.replace(nation, results);
        try {
            int saved = signalRecorder.saveAll(records);
            log.info("[Screening] {} 신호 기록 {}건 저장", nation, saved);
        } catch (Exception e) {
            log.warn("[Screening] {} 신호 기록 저장 실패 (스크리닝 결과는 저장됨): {}", nation, e.getMessage());
        }
        log.info("[Screening] {} {}/{}개 분석 완료 ({}초) — {}", nation, results.size(), targets.size(),
                (System.currentTimeMillis() - started) / 1000, funnel(results));
    }

    /** 지금 방식(지수 ETF 포함)으로 분석한 결과가 있는지 */
    public boolean hasCurrentResult(Nation nation) {
        return repository.existsByNationAndCategoryIsNotNull(nation);
    }

    /** 분석 대상 한 개. rank: 시가총액 순위 */
    private record Target(String code, String name, BigDecimal marketCap, ScreeningCategory category, int rank, boolean momentumExtra) {}

    /** 지수 ETF: stock 테이블에 있는 것만 */
    private List<Target> pickIndexEtfs(Nation nation) {
        List<IndexEtf> etfs = IndexEtf.of(nation);
        Set<String> known = stockRepository.findAllById(etfs.stream().map(IndexEtf::code).toList()).stream()
                .map(Stock::getCode)
                .collect(Collectors.toSet());

        List<Target> targets = new ArrayList<>();
        for (IndexEtf etf : etfs) {
            if (!known.contains(etf.code())) {
                log.warn("[Screening] 지수 ETF {}({})가 stock 테이블에 없어 건너뜀", etf.code(), etf.label());
                continue;
            }
            targets.add(new Target(etf.code(), etf.label(), null, ScreeningCategory.INDEX_ETF, 0, false));
        }
        return targets;
    }

    /** 시가총액 순으로, stock 테이블에 있는 종목만 */
    private List<Target> pickStocks(Nation nation, int size) {
        List<MarketCapStock> ranked = marketCapProvider.fetchByMarketCap(nation);
        if (ranked.isEmpty()) return List.of();

        List<MarketCapStock> pool = ranked.stream().limit(size * 3L).toList();
        Map<String, Stock> known = stockRepository.findAllById(pool.stream().map(MarketCapStock::code).toList()).stream()
                .collect(Collectors.toMap(Stock::getCode, Function.identity(), (a, b) -> a));

        List<Target> targets = new ArrayList<>();
        for (MarketCapStock s : pool) {
            Stock stock = known.get(s.code());
            if (stock == null || stock.getNation() != nation) continue;
            String name = (s.name() != null && !s.name().isBlank()) ? s.name() : stock.getName();
            targets.add(new Target(s.code(), name, s.marketCap(), ScreeningCategory.STOCK, targets.size() + 1, false));
            if (targets.size() >= size) break;
        }
        if (targets.size() < size) {
            log.info("[Screening] {} 시가총액 상위 {}개 중 stock 테이블에 있는 종목 {}개", nation, pool.size(), targets.size());
        }
        return targets;
    }

    /** 최신 모멘텀 순위에서 상위 20% 중 이미 대상인 종목을 뺀 나머지. 순위가 없으면 빈 목록 */
    private List<Target> pickMomentumExtras(Set<String> have) {
        try {
            var asOf = momentumRepository.findLatestAsOf();
            if (asOf == null) return List.of();
            List<MomentumRank> top = momentumRepository.findByAsOfOrderByRankNoAsc(asOf).stream()
                    .filter(m -> m.getPct() <= MomentumService.CANDIDATE_PCT)
                    .filter(m -> !have.contains(m.getCode()))
                    .toList();
            if (top.isEmpty()) return List.of();
            Map<String, Stock> known = stockRepository.findAllById(top.stream().map(MomentumRank::getCode).toList()).stream()
                    .collect(Collectors.toMap(Stock::getCode, Function.identity(), (a, b) -> a));
            List<Target> out = new ArrayList<>();
            for (MomentumRank m : top) {
                Stock st = known.get(m.getCode());
                if (st == null || st.getNation() != Nation.US) continue;
                String name = st.getName() != null && !st.getName().isBlank() ? st.getName() : m.getName();
                out.add(new Target(m.getCode(), name, null, ScreeningCategory.STOCK, 0, true));
            }
            if (out.size() < top.size()) log.info("[Screening] 모멘텀 추가 후보 {}개 중 stock 테이블에 있는 미국 종목 {}개", top.size(), out.size());
            return out;
        } catch (Exception e) {
            log.warn("[Screening] 모멘텀 추가 종목 조회 실패 → 시가총액 상위만 분석: {}", e.getMessage());
            return List.of();
        }
    }

    /** previousCandidates == null 이면 NEW 표시를 하지 않는다 */
    private ScreeningResult analyze(Nation nation, Target target, LocalDateTime analyzedAt, Set<String> previousCandidates, List<SignalRecord> records) {
        String code = target.code();
        List<StockChartDto> daily = chartService.getDailyChartDtoAsc(code);
        List<StockChartDto> weekly = chartService.getWeeklyChartDtoAsc(code);
        StockPriceDto price = priceService.fetchPrice(code);

        MomentumScore momentum = momentumAnalyzer.analyze(daily, price);
        TechnicalScore technical = technicalAnalyzer.analyze(daily, weekly);
        MarketContext ctx = MarketContext.of(daily, weekly, ChartDataType.STOCK, ExchangeSession.fromCode(code));
        TradingSignalResult signal = tradingSignalAnalyzer.analyze(code, ctx);

        TradingSignalResult.EntryPlan plan = signal.entryPlan();
        Verdict verdict = toVerdict(signal.verdict());
        ChartPhase phase = signal.analysis() != null ? signal.analysis().phase() : ChartPhase.INSUFFICIENT_DATA;
        ScreeningStage stage = stageOf(phase, ctx, plan);
        EntryType rawType = PhaseIndicators.classifyEntryTypeIgnoringPhase(ctx);
        boolean isNew = previousCandidates != null
                && CANDIDATE_VERDICTS.contains(verdict)
                && !previousCandidates.contains(code);

        if (target.category() == ScreeningCategory.STOCK && !target.momentumExtra()) {
            SignalRecord rec = signalRecorder.snapshot(nation, code, daily, weekly, ctx, signal, analyzedAt);
            if (rec != null) records.add(rec);
        }

        TradingSignalResult.PriceTarget pt = signal.priceTarget();
        return ScreeningResult.builder()
                .stockCode(code)
                .stockName(target.name())
                .nation(nation)
                .category(target.category())
                .marketCapRank(target.rank())
                .marketCap(target.marketCap())
                .currentPrice(price != null ? price.currentPrice() : null)
                .chartPhase(signal.analysis() != null ? signal.analysis().phase() : null)
                .verdict(verdict)
                .entryType(plan != null ? plan.type() : null)
                .momentumScore(momentum.getScore())
                .technicalScore(technical.getScore())
                .primaryEntry(pt != null ? pt.primaryEntry() : 0)
                .stopLoss(pt != null ? pt.stopLoss() : 0)
                .primaryTarget(pt != null ? pt.primaryTarget() : 0)
                .riskReward(plan != null ? plan.riskReward() : 0)
                .trailingStop(pt != null ? pt.trailingStop() : null)
                .holdTarget(pt != null ? pt.holdTarget() : null)
                .zoneLow(hasZone(plan) ? plan.zoneLow() : null)
                .zoneHigh(hasZone(plan) ? plan.zoneHigh() : null)
                .newCandidate(isNew)
                .rejectStage(stage)
                .rawEntryType(rawType)
                .headline(abbreviate(signal.narrative() != null ? signal.narrative().headline() : null, 200))
                .analyzedAt(analyzedAt)
                .build();
    }

    /** 정식 진입 계획(돌파·탈환·눌림)의 구간만 저장한다 */
    private static boolean hasZone(TradingSignalResult.EntryPlan plan) {
        return plan != null && EntryPlanner.isPlannableType(plan.type()) && plan.zoneHigh() > 0;
    }

    /** 타점 엔진과 같은 순서로 관문을 다시 따라가며 어디서 멈췄는지 본다 */
    private static ScreeningStage stageOf(ChartPhase phase, MarketContext ctx, TradingSignalResult.EntryPlan plan) {
        if (phase == null || phase == ChartPhase.INSUFFICIENT_DATA) return ScreeningStage.INSUFFICIENT_DATA;
        if (!EntryPlanner.isPlannablePhase(phase)) return ScreeningStage.PHASE_BLOCKED;
        if (plan == null || !EntryPlanner.isPlannableType(plan.type())) return ScreeningStage.NO_SETUP;
        return ScreeningStage.PLANNED;
    }

    /** 실행 로그용 관문별 집계 */
    private static String funnel(List<ScreeningResult> results) {
        Map<ScreeningStage, Long> stages = results.stream().filter(r -> r.getRejectStage() != null)
                .collect(Collectors.groupingBy(ScreeningResult::getRejectStage, () -> new EnumMap<>(ScreeningStage.class), Collectors.counting()));
        Map<EntryType, Long> raw = results.stream().filter(r -> r.getRawEntryType() != null)
                .collect(Collectors.groupingBy(ScreeningResult::getRawEntryType, () -> new EnumMap<>(EntryType.class), Collectors.counting()));
        return "관문 " + stages + " · 국면 무시 시 진입 방식 " + raw;
    }

    /** 타점 엔진은 판정을 문자열로 준다 */
    private static Verdict toVerdict(String verdict) {
        if (verdict == null) return Verdict.NO_SETUP;
        try {
            return Verdict.valueOf(verdict);
        } catch (IllegalArgumentException e) {
            return Verdict.NO_SETUP;
        }
    }

    // 조회 (홈 화면)

    /** 지수 ETF: 목록 순서대로 전부 */
    @Transactional(readOnly = true)
    public ScreeningResponse getCandidates(int size) {
        List<ScreeningResult> all = repository.findAll();
        int limit = Math.max(1, size);
        Map<String, MomentumRank> mo = latestMomentum();
        Comparator<ScreeningResult> momentumFirst = Comparator.comparing((ScreeningResult r) -> isMomentumTop(r, mo), Comparator.reverseOrder());
        Map<String, String> sectors = sectorsOf(all);
        Function<ScreeningResult, ScreeningCandidate> toDto = r -> ScreeningCandidate.from(r, momentumOf(r, mo), sectors.get(r.getStockCode()));

        List<ScreeningCandidate> indexEtfs = all.stream()
                .filter(ScreeningResult::isIndexEtf)
                .sorted(Comparator.comparingInt(r -> IndexEtf.orderOf(r.getStockCode())))
                .map((ScreeningResult r) -> ScreeningCandidate.from(r))
                .toList();

        List<ScreeningResult> stocks = all.stream().filter(r -> !r.isIndexEtf()).toList();

        List<ScreeningCandidate> candidates = stocks.stream()
                .filter(r -> CANDIDATE_VERDICTS.contains(r.getVerdict()))
                .sorted(Comparator.<ScreeningResult>comparingInt(r -> CANDIDATE_VERDICTS.indexOf(r.getVerdict()))
                        .thenComparing(momentumFirst)                                                 // 같은 판정이면 모멘텀 상위 20% 먼저
                        .thenComparing(ScreeningResult::isNewCandidate, Comparator.reverseOrder())   // 같은 판정이면 새로 들어온 종목 먼저
                        .thenComparing(Comparator.comparingInt(ScreeningResult::getScore).reversed())
                        .thenComparingInt(ScreeningResult::getMarketCapRank))
                .limit(limit)
                .map(toDto)
                .toList();

        List<ScreeningCandidate> watching = stocks.stream()
                .filter(r -> !CANDIDATE_VERDICTS.contains(r.getVerdict()))
                .filter(r -> WATCHING_PHASES.contains(r.getChartPhase()))
                .sorted(Comparator.<ScreeningResult>comparingInt(r -> WATCHING_PHASES.indexOf(r.getChartPhase()))
                        .thenComparing(momentumFirst)
                        .thenComparing(Comparator.comparingInt(ScreeningResult::getScore).reversed())
                        .thenComparingInt(ScreeningResult::getMarketCapRank))
                .limit(limit)
                .map(toDto)
                .toList();

        // 상승 추세: 엔진이 "새로 사기보다 보유"로 보는 강력 보유 국면, 점수 높은 순
        List<ScreeningCandidate> trending = stocks.stream()
                .filter(r -> r.getChartPhase() == ChartPhase.STRONG_HOLD)
                .filter(r -> !CANDIDATE_VERDICTS.contains(r.getVerdict()))
                .sorted(momentumFirst
                        .thenComparing(Comparator.comparingInt(ScreeningResult::getScore).reversed())
                        .thenComparingInt(ScreeningResult::getMarketCapRank))
                .limit(limit)
                .map(toDto)
                .toList();

        return new ScreeningResponse(latest(all, Nation.KR), latest(all, Nation.US), all.size(),
                withRsi2(indexEtfs), withRsi2(candidates), withRsi2(watching), withRsi2(trending));
    }

    /** RSI2 눌림(참고)을 붙인다 */
    private List<ScreeningCandidate> withRsi2(List<ScreeningCandidate> list) {
        return list.stream().map(c -> {
            try {
                var r = rsi2Service.of(c.code());
                return r == null ? c : c.withRsi2(r.rsi2(), r.dip(), r.fear());
            } catch (Exception e) {
                return c;
            }
        }).toList();
    }

    /** 종목코드 → 섹터 (화면 정렬용). 조회 실패나 빈 값은 빼고, 목록은 그대로 보여 준다 */
    private Map<String, String> sectorsOf(List<ScreeningResult> all) {
        Map<String, String> out = new HashMap<>();
        try {
            for (Stock s : stockRepository.findAllById(all.stream().map(ScreeningResult::getStockCode).distinct().toList())) {
                if (s.getSector() != null && !s.getSector().isBlank()) out.put(s.getCode(), s.getSector().trim());
            }
        } catch (Exception e) {
            log.warn("[Screening] 섹터 조회 실패 → 섹터 없이 표시: {}", e.getMessage());
        }
        return out;
    }

    /** 최신 모멘텀 순위 (종목코드 → 순위). 계산 전이거나 조회 실패면 빈 맵 */
    private Map<String, MomentumRank> latestMomentum() {
        try {
            var asOf = momentumRepository.findLatestAsOf();
            if (asOf == null) return Map.of();
            return momentumRepository.findByAsOfOrderByRankNoAsc(asOf).stream()
                    .collect(Collectors.toMap(MomentumRank::getCode, Function.identity(), (a, b) -> a));
        } catch (Exception e) {
            log.warn("[Screening] 모멘텀 순위 조회 실패 → 모멘텀 없이 정렬: {}", e.getMessage());
            return Map.of();
        }
    }

    /** 미국 종목만 */
    private static MomentumRank momentumOf(ScreeningResult r, Map<String, MomentumRank> mo) {
        return r.getNation() == Nation.US ? mo.get(r.getStockCode()) : null;
    }

    private static boolean isMomentumTop(ScreeningResult r, Map<String, MomentumRank> mo) {
        MomentumRank m = momentumOf(r, mo);
        return m != null && m.getPct() <= MomentumService.CANDIDATE_PCT;
    }

    /** 지수 ETF 분석 결과 (종목코드 → 결과). 홈 상단 지수 띠에서 국면 배지로 쓴다 */
    @Transactional(readOnly = true)
    public Map<String, ScreeningCandidate> getIndexEtfAnalysis() {
        return repository.findAllByCategory(ScreeningCategory.INDEX_ETF).stream()
                .collect(Collectors.toMap(ScreeningResult::getStockCode, (ScreeningResult r) -> ScreeningCandidate.from(r), (a, b) -> a));
    }

    private static LocalDateTime latest(List<ScreeningResult> all, Nation nation) {
        return all.stream()
                .filter(r -> r.getNation() == nation)
                .map(ScreeningResult::getAnalyzedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private static String abbreviate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static void pause() {
        try {
            Thread.sleep(PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
