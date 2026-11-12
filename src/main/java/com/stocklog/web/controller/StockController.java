// StockApiController.java 의 기존 분석 API 엔드포인트는 그대로 두고 페이지 컨트롤러를 별도로 추가

package com.stocklog.web.controller;

import com.stocklog.modules.member.dto.response.MemberResponse;
import com.stocklog.modules.stock.analysis.StockAnalysisService;
import com.stocklog.modules.stock.analysis.result.StockAnalysisResult;
import com.stocklog.modules.stock.analysis.signal.SignalDescription;
import com.stocklog.modules.stock.analysis.signal.SignalGroup;
import com.stocklog.modules.stock.application.response.StockSnapshot;
import com.stocklog.global.annotation.LoginUser;
import com.stocklog.modules.stock.application.usecase.StockSnapshotUseCase;
import com.stocklog.modules.stock.analysis.signal.trading.TradingSignalResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/stock")
public class StockController {

    private final StockSnapshotUseCase snapshotUseCase;
    private final StockAnalysisService analysisService;

    @GetMapping("/analysis")
    public String analysisPage(@RequestParam String code,
                               @LoginUser MemberResponse loginMember,
                               Model model) {
        if (loginMember == null) return "redirect:/auth/login";

        StockSnapshot stockSnapshot = snapshotUseCase.getStockSnapshot(code);
        StockAnalysisResult analysisResult =  analysisService.getAnalysisResult(stockSnapshot);
        // 타점 엔진 결과는 getAnalysisResult 안에서 이미 계산되어 있으므로 다시 호출하지 않는다
        TradingSignalResult tradingSignal = analysisResult.getTradingSignalResult();

        // StockAnalysisResult에서 signals 필드가 빠졌으므로 도메인별 신호를 여기서 합친다
        List<String> allSignals = new ArrayList<>();
        allSignals.addAll(analysisResult.getValuation().getSignals());
        allSignals.addAll(analysisResult.getMomentum().getSignals());
        allSignals.addAll(analysisResult.getTechnical().getSignals());

        Map<String, String> signalDescMap = allSignals.stream()
                .collect(Collectors.toMap(
                        s -> s,
                        SignalDescription::get,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        double valuationPercent = (analysisResult.getValuation().getScore() / 40.0) * 100;
        double momentumPercent = (analysisResult.getMomentum().getScore() / 30.0) * 100;
        double technicalPercent = (analysisResult.getTechnical().getScore() / 30.0) * 100;
        double ringOffset = 502.7 - (analysisResult.getScore() / 100.0 * 502.7);


        model.addAttribute("revenueGrowthPositive",
                stockSnapshot.financial() != null &&
                        stockSnapshot.financial().revenueGrowth() != null &&
                        stockSnapshot.financial().revenueGrowth().compareTo(BigDecimal.ZERO) >= 0);

        model.addAttribute("epsGrowthPositive",
                stockSnapshot.financial() != null &&
                        stockSnapshot.financial().epsGrowth() != null &&
                        stockSnapshot.financial().epsGrowth().compareTo(BigDecimal.ZERO) >= 0);

        model.addAttribute("stockCode", code);
        model.addAttribute("valuationPercent", valuationPercent);
        model.addAttribute("momentumPercent", momentumPercent);
        model.addAttribute("technicalPercent", technicalPercent);
        model.addAttribute("ringOffset", ringOffset);

        model.addAttribute("isEtf", analysisResult.isEtf());
        model.addAttribute("loginMember", loginMember);
        model.addAttribute("analysis", analysisResult);
        model.addAttribute("tradingSignal", tradingSignal);
        model.addAttribute("stockSnapShot", stockSnapshot);
        model.addAttribute("signals", allSignals);
        model.addAttribute("signalDescMap", signalDescMap);
        // 전체 시그널 탭: 영역별로, 문장을 제목·결론·설명·톤으로 나눠 좋음 → 주의 → 참고 순
        boolean etf = analysisResult.isEtf();
        model.addAttribute("signalGroups", List.of(
                SignalGroup.of(etf ? "규모 · 배당" : "재무 · 가치",
                        etf ? "AUM 규모와 분배율로 상품의 안정성과 인컴 매력을 봐요." : "ROE · 마진율 · PER · PBR로 내재 가치를 봐요.",
                        analysisResult.getValuation().getScore(), 40, analysisResult.getValuation().getSignals()),
                SignalGroup.of("모멘텀", "52주 위치 · RSI 극단값 · 거래량으로 단기 수급 에너지를 봐요.",
                        analysisResult.getMomentum().getScore(), 30, analysisResult.getMomentum().getSignals()),
                SignalGroup.of("기술적 지표",
                        etf ? "이동평균선과 변동성으로 추세를 봐요." : "이동평균 배열 · 골든/데드 크로스 · 볼린저 응축 같은 차트 구조를 봐요.",
                        analysisResult.getTechnical().getScore(), 30, analysisResult.getTechnical().getSignals())));
        model.addAttribute("gradeColor", gradeColor(analysisResult.getGrade()));
        model.addAttribute("insightMessage", insightMessage(analysisResult.getGrade()));
        return "stock/analysis";
    }

    private String gradeColor(String grade) {
        return switch (grade) {
            case "S" -> "#f04452";
            case "A" -> "#3182f6";
            case "B" -> "#05c072";
            case "C" -> "#f5a623";
            default  -> "#8b95a1";
        };
    }

    private String insightMessage(String grade) {
        return switch (grade) {
            case "S" -> "모든 지표가 우수합니다. 강력한 매수 후보 종목입니다.";
            case "A" -> "전반적으로 양호한 종목입니다. 적극 관심을 가져볼 만합니다.";
            case "B" -> "보통 수준의 종목입니다. 추가 분석 후 신중히 접근하세요.";
            case "C" -> "일부 지표가 부진합니다. 리스크를 충분히 검토하세요.";
            default  -> "지표가 전반적으로 부진하거나 위험 신호가 있습니다.";
        };
    }
}