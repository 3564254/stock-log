package com.stocklog.modules.market.newssector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stocklog.modules.ai.AiException;
import com.stocklog.modules.ai.AiProvider;
import com.stocklog.modules.market.news.dto.NewsDto;
import com.stocklog.modules.market.service.NewsService;
import com.stocklog.modules.purchase.portfolio.entity.Holding;
import com.stocklog.modules.purchase.portfolio.repository.HoldingRepository;
import com.stocklog.modules.stock.price.StockPriceService;
import com.stocklog.modules.stock.price.dto.StockPriceDto;
import com.stocklog.modules.stock.stock.Stock;
import com.stocklog.modules.stock.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/** 오늘 뉴스 → 섹터 영향 (표시만, 매매 신호 아님) */
@Service
@Slf4j
@RequiredArgsConstructor
public class NewsSectorService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** key → [이름, 대표 ETF, 종목 마스터 섹터들(| 구분)] */
    static final Map<String, String[]> SECTORS = new LinkedHashMap<>();
    static {
        SECTORS.put("energy", new String[]{"에너지", "XLE", "전통 화석 에너지"});
        SECTORS.put("oil", new String[]{"원유", "USO", "전통 화석 에너지"});
        SECTORS.put("defense", new String[]{"방산·우주", "ITA", "우주항공 및 국방"});
        SECTORS.put("travel", new String[]{"항공·여행", "JETS", "외식, 여행 및 레저|운송 및 물류"});
        SECTORS.put("semis", new String[]{"반도체", "SOXX", "반도체 및 반도체장비"});
        SECTORS.put("tech", new String[]{"기술·소프트웨어", "XLK", "소프트웨어 및 IT 서비스|하드웨어 및 전자 장비"});
        SECTORS.put("financials", new String[]{"금융", "XLF", "은행 및 신용 서비스|보험|자본 시장 및 자산 운용"});
        SECTORS.put("health", new String[]{"헬스케어", "XLV", "의료 기기 및 장비|헬스케어 서비스 및 유통"});
        SECTORS.put("bio", new String[]{"바이오·제약", "XBI", "제약 및 생명공학"});
        SECTORS.put("discretionary", new String[]{"경기소비재·자동차", "XLY", "내구소비재 및 의류|전자상거래 및 플랫폼 소매|오프라인 유통 및 소매|순수 자동차 및 모빌리티"});
        SECTORS.put("staples", new String[]{"필수소비재", "XLP", "식음료 및 담배|가정용품 및 개인용품"});
        SECTORS.put("industrials", new String[]{"산업재", "XLI", "기계 및 산업 장비|건설 및 엔지니어링|상업 및 전문 서비스"});
        SECTORS.put("materials", new String[]{"소재·금속", "XLB", "화학|철강 및 산업용 금속|건축자재 및 포장"});
        SECTORS.put("utilities", new String[]{"유틸리티·친환경", "XLU", "유틸리티 (전력/수도 등)|친환경 및 재생 에너지"});
        SECTORS.put("realestate", new String[]{"부동산", "XLRE", "부동산 및 리츠(REITs)"});
        SECTORS.put("comm", new String[]{"통신·미디어", "XLC", "통신 서비스|미디어 및 엔터테인먼트"});
        SECTORS.put("gold", new String[]{"금", "GLD", "귀금속 및 특수 광물"});
        SECTORS.put("bonds", new String[]{"장기 국채", "TLT", ""});
        SECTORS.put("china", new String[]{"중국", "FXI", ""});
    }

    private final AiProvider aiProvider;
    private final NewsService newsService;
    private final NewsSectorRepository repository;
    private final StockPriceService priceService;
    private final HoldingRepository holdingRepository;
    private final StockRepository stockRepository;

    public record Sector(String key, String label, String etf, int dir, String why, Double chg, Double since, List<String> held) {}
    public record Item(String title, List<Sector> sectors) {}
    public record View(LocalDate day, LocalDateTime createdAt, boolean today, List<Item> items) {}

    @Transactional(readOnly = true)
    public Optional<View> latest(Long memberId) {
        return repository.findTopByOrderByDayDesc().map(e -> toView(e, memberId));
    }

    @Transactional
    public View generate(Long memberId) {
        LocalDate day = LocalDate.now(KST);
        var existing = repository.findByDay(day);
        if (existing.isPresent()) return toView(existing.get(), memberId);

        List<NewsDto> news = new ArrayList<>();
        try { news.addAll(newsService.getMarketNewsDESCTopN(12)); } catch (Exception e) { log.debug("[뉴스섹터] 시장 뉴스 실패 {}", e.getMessage()); }
        try { news.addAll(newsService.getKoreanHeadlines(6)); } catch (Exception e) { log.debug("[뉴스섹터] 국내 뉴스 실패 {}", e.getMessage()); }
        Set<String> seen = new HashSet<>();
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (NewsDto d : news) {
            if (d == null || d.headline() == null || d.headline().isBlank()) continue;
            String h = d.headline().trim();
            if (!seen.add(h.toLowerCase())) continue;
            sb.append(++n).append(". ").append(h.length() > 160 ? h.substring(0, 160) : h);
            if (d.dateTime() != null) sb.append(" (").append(d.dateTime().toLocalDate()).append(')');
            sb.append('\n');
            if (n >= 15) break;
        }
        if (n == 0) throw new AiException(AiException.Kind.BLOCKED, "no news");

        StringBuilder keys = new StringBuilder();
        SECTORS.forEach((k, v) -> keys.append(k).append(" = ").append(v[0]).append('\n'));
        String raw = aiProvider.getNewsSectorImpact(sb.toString(), keys.toString());

        ObjectNode root = MAPPER.createObjectNode();
        var arr = root.putArray("items");
        Set<String> etfs = new LinkedHashSet<>();
        try {
            String s = raw == null ? "" : raw;
            int a = s.indexOf('{'), b = s.lastIndexOf('}');
            JsonNode in = MAPPER.readTree(a >= 0 && b > a ? s.substring(a, b + 1) : "{}");
            for (JsonNode it : in.path("items")) {
                String title = it.path("title").asText("").trim();
                if (title.isEmpty()) continue;
                ObjectNode o = arr.addObject();
                o.put("title", title.length() > 80 ? title.substring(0, 80) : title);
                var ss = o.putArray("sectors");
                Set<String> used = new HashSet<>();
                for (JsonNode sc : it.path("sectors")) {
                    String key = sc.path("key").asText("");
                    int dir = sc.path("dir").asInt(0);
                    if (!SECTORS.containsKey(key) || dir == 0 || !used.add(key)) continue;
                    ObjectNode x = ss.addObject();
                    x.put("key", key);
                    x.put("dir", dir > 0 ? 1 : -1);
                    String why = sc.path("why").asText("");
                    x.put("why", why.length() > 80 ? why.substring(0, 80) : why);
                    etfs.add(SECTORS.get(key)[1]);
                    if (ss.size() >= 4) break;
                }
                if (ss.isEmpty()) arr.remove(arr.size() - 1);
                if (arr.size() >= 6) break;
            }
        } catch (Exception e) {
            throw new AiException(AiException.Kind.ERROR, "parse: " + e.getMessage());
        }
        // 판단 시점 ETF 가격 (나중에 맞춰 보기용)
        ObjectNode base = root.putObject("base");
        Map<String, StockPriceDto> px = prices(etfs);
        px.forEach((k, v) -> { if (v != null && v.currentPrice() != null) base.put(k, v.currentPrice()); });

        LocalDateTime now = LocalDateTime.now(KST);
        NewsSectorEntity saved = repository.save(new NewsSectorEntity(day, root.toString(), now));
        return toView(saved, memberId, px);
    }

    private View toView(NewsSectorEntity e, Long memberId) { return toView(e, memberId, null); }

    private View toView(NewsSectorEntity e, Long memberId, Map<String, StockPriceDto> px) {
        JsonNode root;
        try { root = MAPPER.readTree(e.getContent()); } catch (Exception ex) { root = MAPPER.createObjectNode(); }
        Set<String> etfs = new LinkedHashSet<>();
        for (JsonNode it : root.path("items")) for (JsonNode sc : it.path("sectors")) {
            String[] s = SECTORS.get(sc.path("key").asText(""));
            if (s != null) etfs.add(s[1]);
        }
        if (px == null) px = prices(etfs);
        Map<String, List<String>> held = heldBySector(memberId);
        List<Item> items = new ArrayList<>();
        for (JsonNode it : root.path("items")) {
            List<Sector> list = new ArrayList<>();
            for (JsonNode sc : it.path("sectors")) {
                String key = sc.path("key").asText("");
                String[] s = SECTORS.get(key);
                if (s == null) continue;
                StockPriceDto p = px.get(s[1]);
                Double chg = p != null && p.changeRate() != null ? p.changeRate().doubleValue() : null;
                Double since = null;
                JsonNode b = root.path("base").path(s[1]);
                if (b.isNumber() && b.asDouble() > 0 && p != null && p.currentPrice() != null && p.currentPrice().signum() > 0) {
                    since = (p.currentPrice().doubleValue() / b.asDouble() - 1) * 100;
                }
                list.add(new Sector(key, s[0], s[1], sc.path("dir").asInt(), sc.path("why").asText(""), chg, since,
                        held.getOrDefault(key, List.of())));
            }
            if (!list.isEmpty()) items.add(new Item(it.path("title").asText(""), list));
        }
        return new View(e.getDay(), e.getCreatedAt(), e.getDay().equals(LocalDate.now(KST)), items);
    }

    private Map<String, StockPriceDto> prices(Collection<String> etfs) {
        if (etfs.isEmpty()) return Map.of();
        try { return priceService.fetchPricesInBulk(new ArrayList<>(etfs)); }
        catch (Exception e) { log.debug("[뉴스섹터] ETF 가격 실패 {}", e.getMessage()); return Map.of(); }
    }

    /** 섹터 key → 그 섹터에 속하는 내 보유 종목 이름 */
    private Map<String, List<String>> heldBySector(Long memberId) {
        Map<String, List<String>> out = new HashMap<>();
        if (memberId == null) return out;
        try {
            List<Holding> hs = holdingRepository.findAllByMemberId(memberId);
            List<String> codes = hs.stream().map(h -> h.getStock().getCode()).distinct().toList();
            Map<String, Stock> stocks = new HashMap<>();
            stockRepository.findAllById(codes).forEach(s -> stocks.put(s.getCode(), s));
            for (String code : codes) {
                Stock s = stocks.get(code);
                String name = s != null && s.getName() != null ? s.getName() : code;
                String sec = s != null && s.getSector() != null ? s.getSector().trim() : "";
                SECTORS.forEach((k, v) -> {
                    boolean bySector = !sec.isEmpty() && !v[2].isEmpty() && Arrays.asList(v[2].split("\\|")).contains(sec);
                    if (bySector || v[1].equalsIgnoreCase(code)) out.computeIfAbsent(k, x -> new ArrayList<>()).add(name);
                });
            }
        } catch (Exception e) {
            log.debug("[뉴스섹터] 보유 종목 섹터 실패 {}", e.getMessage());
        }
        return out;
    }
}
