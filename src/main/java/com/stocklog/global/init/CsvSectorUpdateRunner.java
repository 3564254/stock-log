//package com.stocklog.global.init;
//
//import com.stocklog.domain.stock.dto.request.StockRequest;
//import com.stocklog.domain.stock.dto.vo.info.StockInfoDetail;
//import com.stocklog.domain.stock.type.MarketType;
//import com.stocklog.domain.stock.type.Nation;
//import com.stocklog.infra.kis.info.api.KisDomesticInfoProvider;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.stereotype.Component;
//
//import java.nio.charset.StandardCharsets;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//@Component
//@RequiredArgsConstructor
//@Slf4j
//public class CsvSectorUpdateRunner implements CommandLineRunner {
//
//    private final KisDomesticInfoProvider kisDomesticInfoProvider;
//
//    @Override
//    public void run(String... args) throws Exception {
//        log.info("🚀 실전투자용(20 TPS) KIS API 연동 CSV 업데이트 시작...");
//
//        // 🌟 수정된 파일 경로 (src/main/resources/data/ 폴더 기준)
//        Path inputPath = Paths.get("src/main/resources/data/stocks_master_gics3_korean.csv");
//        Path outputPath = Paths.get("src/main/resources/data/stocks_master_final.csv");
//
//        if (!Files.exists(inputPath)) {
//            log.warn("❌ 입력 파일이 존재하지 않습니다. 경로를 확인해주세요: {}", inputPath.toAbsolutePath());
//            return;
//        }
//
//        List<String> lines = Files.readAllLines(inputPath, StandardCharsets.UTF_8);
//        List<String> updatedLines = new ArrayList<>();
//
//        // 첫 번째 줄(헤더) 추가
//        updatedLines.add(lines.get(0));
//
//        Map<String, String> krxToGicsMap = getKrxToGicsMap();
//        int successCount = 0;
//
//        for (int i = 1; i < lines.size(); i++) {
//            String line = lines.get(i);
//
//            // CSV 큰따옴표 안의 쉼표는 무시하고 분리
//            String[] cols = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
//
//            if (cols.length < 7) {
//                updatedLines.add(line);
//                continue;
//            }
//
//            String code = cols[0].replace("\"", "").trim();
//            String name = cols[2].replace("\"", "").trim();
//            String marketStr = cols[3].replace("\"", "").trim();
//            String nationStr = cols[4].replace("\"", "").trim();
//
//            // 한국 주식(KR)이 아니거나 ETF면 API 호출 건너뛰기
//            if (!"KR".equals(nationStr) || isEtfOrTheme(name)) {
//                updatedLines.add(line);
//                continue;
//            }
//
//            try {
//                MarketType marketType = MarketType.valueOf(marketStr);
//                Nation nation = Nation.valueOf(nationStr);
//
//                StockRequest request = new StockRequest(code, marketType, nation);
//                StockInfoDetail detail = kisDomesticInfoProvider.fetchInfo(request);
//
//                // 🌟 업종 Getter 적용 완료
//                String kisSector = detail.getIndexSmall();
//
//                if (kisSector != null && !kisSector.isBlank() && !"Unknown".equals(kisSector)) {
//                    String gicsSector = krxToGicsMap.getOrDefault(kisSector, kisSector);
//                    cols[6] = "\"" + gicsSector + "\"";
//                    successCount++;
//                }
//
//                updatedLines.add(String.join(",", cols));
//
//                // 🌟 실전투자 API 20 TPS 제한 방어 (60ms 대기)
//                Thread.sleep(60);
//
//            } catch (IllegalArgumentException e) {
//                log.error("❌ Enum 변환 실패 [{}]: market={}, nation={}", name, marketStr, nationStr);
//                updatedLines.add(line);
//            } catch (Exception e) {
//                log.error("❌ 종목 업데이트 실패 [{}]: {}", name, e.getMessage());
//                updatedLines.add(line);
//            }
//        }
//
//        // 새로운 CSV 파일 쓰기 (BOM 적용)
//        byte[] bom = new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF};
//        try (java.io.OutputStream os = Files.newOutputStream(outputPath)) {
//            os.write(bom);
//            os.write(String.join("\n", updatedLines).getBytes(StandardCharsets.UTF_8));
//        }
//
//        log.info("✅ 데이터 변환 완료! 최종 완성본 파일이 생성되었습니다: {} (업데이트 성공: {}건)", outputPath.toAbsolutePath(), successCount);
//    }
//
//    private boolean isEtfOrTheme(String name) {
//        if (name == null) return true;
//        String upperName = name.toUpperCase();
//        String[] keywords = {"ETF", "ETN", "TIGER", "KODEX", "KBSTAR", "ARIRANG", "KOSEF", "HANARO", "ACE", "PLUS", "RISE", "마이티", "히어로즈"};
//        for (String keyword : keywords) {
//            if (upperName.contains(keyword)) return true;
//        }
//        return false;
//    }
//
//    private Map<String, String> getKrxToGicsMap() {
//        Map<String, String> map = new HashMap<>();
//        map.put("의약품", "제약, 생명공학 및 생명과학");
//        map.put("운수장비", "자동차 및 부품");
//        map.put("유통업", "소매");
//        map.put("음식료품", "식품, 음료 및 담배");
//        map.put("서비스업", "IT 서비스");
//        map.put("전기전자", "반도체 장비 및 소재");
//        map.put("금융업", "다각화 은행");
//        map.put("증권", "자본 시장");
//        map.put("보험", "다각화 보험");
//        map.put("화학", "화학");
//        map.put("철강금속", "철강");
//        map.put("기계", "자본재");
//        map.put("건설업", "엔지니어링 및 건설");
//        map.put("통신업", "통신 서비스");
//        map.put("전기가스업", "다각화 유틸리티");
//        map.put("운수창고", "통합 화물 및 물류");
//        map.put("종이목재", "종이 및 종이 제품");
//        map.put("비금속광물", "건축 자재");
//        map.put("의료정밀", "의료 기기 및 소모품");
//        map.put("섬유의복", "의류 제조");
//        return map;
//    }
//}