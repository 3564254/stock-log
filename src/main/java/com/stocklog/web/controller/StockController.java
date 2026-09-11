// StockApiController.java 의 기존 분석 API 엔드포인트는 그대로 두고
// 페이지 컨트롤러를 별도로 추가

package com.stocklog.web.controller;

import com.stocklog.modules.member.dto.response.MemberResponse;
import com.stocklog.modules.stock.analysis.StockAnalysisService;
import com.stocklog.modules.stock.analysis.result.StockAnalysisResult;
import com.stocklog.modules.stock.analysis.result.SignalDescription;
import com.stocklog.modules.stock.application.response.StockSnapshot;
import com.stocklog.global.annotation.LoginUser;
import com.stocklog.modules.stock.application.usecase.StockSnapshotUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

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

        Map<String, String> signalDescMap = analysisResult.getSignals().stream()
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
        model.addAttribute("stockSnapShot", stockSnapshot);
        model.addAttribute("signalDescMap", signalDescMap);
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