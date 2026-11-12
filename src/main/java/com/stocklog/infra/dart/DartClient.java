package com.stocklog.infra.dart;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

/** 금융감독원 OpenDART. 키는 환경변수 DART_API_KEY */
@Component
@Slf4j
public class DartClient {

    private static final String BASE = "https://opendart.fss.or.kr/api";
    private static final Duration CORP_TTL = Duration.ofDays(7);
    private static final Duration CORP_RETRY = Duration.ofMinutes(30);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    private volatile Map<String, String> corpByStock = Map.of();
    private volatile Instant corpLoadedAt = Instant.EPOCH;
    private volatile Instant corpTriedAt = Instant.EPOCH;
    private volatile boolean warnedNoKey = false;

    public DartClient(@Value("${DART_API_KEY:}") String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public boolean enabled() {
        if (apiKey.isEmpty() && !warnedNoKey) {
            warnedNoKey = true;
            log.warn("[DART] 환경변수 DART_API_KEY가 없어 국내 재무 정보를 받을 수 없어요");
        }
        return !apiKey.isEmpty();
    }

    /** 종목코드 → DART 고유번호. 모르면 null */
    public String corpCode(String stockCode) {
        if (!enabled() || stockCode == null) return null;
        ensureCorpCodes();
        return corpByStock.get(stockCode.trim());
    }

    private synchronized void ensureCorpCodes() {
        Instant now = Instant.now();
        boolean fresh = !corpByStock.isEmpty() && corpLoadedAt.plus(CORP_TTL).isAfter(now);
        if (fresh || corpTriedAt.plus(CORP_RETRY).isAfter(now)) return;
        corpTriedAt = now;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + "/corpCode.xml?crtfc_key=" + enc(apiKey)))
                    .timeout(Duration.ofSeconds(60)).GET().build();
            HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200) { log.warn("[DART] 고유번호 목록 HTTP {}", res.statusCode()); return; }
            Map<String, String> map = parseCorpZip(res.body());
            if (map.isEmpty()) { log.warn("[DART] 고유번호 목록이 비었어요 (키 오류일 수 있음): {}", head(res.body())); return; }
            corpByStock = map;
            corpLoadedAt = now;
            log.info("[DART] 상장사 고유번호 {}개 불러옴", map.size());
        } catch (Exception e) {
            log.warn("[DART] 고유번호 목록 받기 실패: {}", e.getMessage());
        }
    }

    /** 상장 회사의 고유번호 목록 (CORPCODE.xml) */
    private static Map<String, String> parseCorpZip(byte[] body) throws Exception {
        Map<String, String> map = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(body))) {
            if (zip.getNextEntry() == null) return map;
            XMLInputFactory f = XMLInputFactory.newInstance();
            f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            XMLStreamReader r = f.createXMLStreamReader((InputStream) zip, "UTF-8");
            String tag = null, corp = null, stock = null;
            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    tag = r.getLocalName();
                    if ("list".equals(tag)) { corp = null; stock = null; }
                } else if (ev == XMLStreamConstants.CHARACTERS && tag != null) {
                    String t = r.getText().trim();
                    if (t.isEmpty()) continue;
                    if ("corp_code".equals(tag)) corp = (corp == null ? "" : corp) + t;
                    else if ("stock_code".equals(tag)) stock = (stock == null ? "" : stock) + t;
                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    if ("list".equals(r.getLocalName()) && corp != null && stock != null && !stock.isBlank()) map.put(stock.trim(), corp.trim());
                    tag = null;
                }
            }
        }
        return map;
    }

    /** 단일회사 주요계정. reprtCode: 11013 1분기 · 11012 반기 · 11014 3분기 · 11011 사업보고서 */
    public List<Account> singleAccounts(String corpCode, int year, String reprtCode) {
        if (!enabled() || corpCode == null) return null;
        try {
            String url = BASE + "/fnlttSinglAcnt.json?crtfc_key=" + enc(apiKey) + "&corp_code=" + enc(corpCode)
                    + "&bsns_year=" + year + "&reprt_code=" + enc(reprtCode);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) { log.warn("[DART] 주요계정 HTTP {} ({} {} {})", res.statusCode(), corpCode, year, reprtCode); return null; }
            AccountResponse body = MAPPER.readValue(res.body(), AccountResponse.class);
            if ("000".equals(body.status())) return body.list() == null ? List.of() : body.list();
            if ("013".equals(body.status())) return List.of();
            log.warn("[DART] 주요계정 status {} {} ({} {} {})", body.status(), body.message(), corpCode, year, reprtCode);
            return null;
        } catch (Exception e) {
            log.warn("[DART] 주요계정 조회 실패 ({} {} {}): {}", corpCode, year, reprtCode, e.getMessage());
            return null;
        }
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private static String head(byte[] b) {
        String s = new String(b, 0, Math.min(b.length, 200), StandardCharsets.UTF_8);
        return s.replaceAll("\\s+", " ");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AccountResponse(String status, String message, List<Account> list) {}

    /** 금액은 콤마 들어간 문자열, 없으면 "" 또는 "-" */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Account(
            @JsonProperty("account_nm") String accountNm,
            @JsonProperty("fs_div") String fsDiv,
            @JsonProperty("sj_div") String sjDiv,
            @JsonProperty("thstrm_amount") String thstrmAmount,
            @JsonProperty("thstrm_add_amount") String thstrmAddAmount,
            @JsonProperty("frmtrm_amount") String frmtrmAmount,
            @JsonProperty("frmtrm_add_amount") String frmtrmAddAmount,
            @JsonProperty("thstrm_dt") String thstrmDt
    ) {}
}
