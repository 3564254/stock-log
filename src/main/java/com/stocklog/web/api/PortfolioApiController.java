package com.stocklog.web.api;


import com.stocklog.domain.ai.AiApiProvider;
import com.stocklog.domain.member.dto.response.MemberResponse;
import com.stocklog.domain.portfolio.analysis.dto.PortfolioAnalysisResult;
import com.stocklog.domain.portfolio.dto.response.PortfolioAnalysisResponse;
import com.stocklog.domain.portfolio.dto.response.PortfolioResponse;
import com.stocklog.domain.portfolio.service.PortfolioAnalysisService;
import com.stocklog.domain.portfolio.service.PortfolioService;
import com.stocklog.global.annotation.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioApiController {

    private final PortfolioService portfolioService;
    private final PortfolioAnalysisService portfolioAnalysisService;
    private final AiApiProvider provider;


    @GetMapping("/ai-comment")
    public ResponseEntity<String> getAiComment(@RequestParam String code, @LoginUser MemberResponse loginMember) {

        PortfolioResponse myPortfolio = portfolioService.getMyPortfolio(loginMember.getId());
        PortfolioAnalysisResponse portfolioAnalysisResponse = portfolioAnalysisService.analyzePortfolio(myPortfolio);

        String comment = provider.getPortfolioAnalysis(PortfolioAnalysisResult.of(myPortfolio.myStocks(), portfolioAnalysisResponse));
        return ResponseEntity.ok(comment);
    }
}
