package com.stocklog.modules.purchase.trade.service;

import com.stocklog.modules.purchase.trade.dto.request.TradingRuleSaveRequest;
import com.stocklog.modules.purchase.trade.dto.response.TradingRuleCheckResponse;
import com.stocklog.modules.purchase.trade.dto.response.TradingRuleResponse;
import com.stocklog.modules.purchase.trade.entity.TradingRule;
import com.stocklog.modules.purchase.trade.repository.TradingRuleRepository;
import com.stocklog.modules.purchase.trade.type.IndicatorType;
import com.stocklog.modules.stock.analysis.domain.signal.vo.MarketContext;
import com.stocklog.modules.stock.analysis.domain.signal.vo.MarketPhase;
import com.stocklog.global.error.BusinessException;
import com.stocklog.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradingRuleService {

    private final TradingRuleRepository tradingRuleRepository;

    @Transactional
    public void addTradingRule(Long memberId, TradingRuleSaveRequest request) {
        tradingRuleRepository.save(
                TradingRule.builder()
                        .memberId(memberId)
                        .title(request.title())
                        .indicatorType(request.indicatorType())
                        .operator(request.operator())
                        .targetValue(request.targetValue())
                        .isActive(true)
                        .build()
        );
    }

    public TradingRuleResponse getActiveRules(Long memberId) {
        List<TradingRule> list = tradingRuleRepository.findAllByMemberIdAndIsActiveTrue(memberId);
        return TradingRuleResponse.of(memberId, list);
    }

    @Transactional
    public void deleteRule(Long memberId, Long tradeRuleId) {
        TradingRule tradingRule = tradingRuleRepository.findById(tradeRuleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADING_RULE_NOT_FOUND));

        validateRuleOwnership(memberId, tradingRule);
        tradingRuleRepository.deleteById(tradeRuleId);
    }

    @Transactional
    public void toggleRule(Long memberId, Long ruleId, Boolean targetState) {
        TradingRule tradingRule = tradingRuleRepository.findById(ruleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRADING_RULE_NOT_FOUND));

        validateRuleOwnership(memberId, tradingRule);
        tradingRule.toggleActive(targetState);
    }

    /**
     * 💡 변경: 검증 시 MarketContext 객체를 직접 주입받아 실시간 데이터를 대조합니다.
     */
    public TradingRuleCheckResponse evaluateBuySignal(Long memberId, MarketContext ctx, MarketPhase phase) {
        List<TradingRule> activeRules = tradingRuleRepository.findAllByMemberIdAndIsActiveTrue(memberId);

        List<TradingRuleCheckResponse.RuleDetail> details = new ArrayList<>();
        boolean isAllPass = true;

        for (TradingRule rule : activeRules) {
            // 단일 룰 평가 수행
            boolean passed = evaluateSingleRule(rule, ctx);

            if (!passed) {
                isAllPass = false;
            }

            details.add(new TradingRuleCheckResponse.RuleDetail(
                    rule.getId(),
                    rule.getTitle(),
                    rule.getIndicatorType(),
                    passed
            ));
        }

        String decision = isAllPass ? "PASS" : "FAIL";
        String verdict = MarketContext.generateEntryVerdict(ctx, phase);

        return new TradingRuleCheckResponse(decision, details, verdict);
    }

    public TradingRuleCheckResponse evaluateSellSignal(Long memberId, MarketContext ctx, MarketPhase phase) {

        return null;
    }





    // ================= 내부 검증 및 헬퍼 메서드 =================

    private void validateRuleOwnership(Long memberId, TradingRule rule) {
        if (!rule.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
    }

    private boolean evaluateSingleRule(TradingRule rule, MarketContext ctx) {
        IndicatorType type = rule.getIndicatorType();

        // 1. 상태형(Boolean) 지표인 경우 -> 수치 비교 없이 Context 속성 자체로 참/거짓 즉시 반환
        if (!type.isNumeric()) {
            return evaluateBooleanIndicator(type, ctx);
        }

        double currentValue = 0.0;

        // 2. 수치형(Numeric) 지표인 경우 -> Operator와 TargetValue 필수 검증
        if (rule.getOperator() == null || rule.getTargetValue() == null) {
            return false; // 세팅 오류 방어
        } else if (rule.getIndicatorType().equals(IndicatorType.STOCK_SCORE)) {
            //TODO: STOCK_SCORE
        } else {
            currentValue = extractNumericIndicator(type, ctx);
        }

        return rule.getOperator().evaluate(currentValue, rule.getTargetValue());
    }

    /**
     * 상태형/이벤트 지표 판별기
     */
    private boolean evaluateBooleanIndicator(IndicatorType type, MarketContext ctx) {
        return switch (type) {
            case UPTREND -> ctx.isUptrend();             // 정배열 여부
            case MACRO_UPTREND -> ctx.isMacroUptrend();  // 장기 추세 여부
            case BOLLINGER_SQUEEZE -> ctx.isSqueeze();   // 볼린저 응축 여부
            case VOLUME_BULLISH -> ctx.isVolBullish();   // 매수세 폭발 여부
            case NEW_HIGH_5D -> ctx.isHH();              // 단기 고점 돌파 여부
            case HIGHER_LOW -> ctx.isHL();               // 저점 방어 여부
            case MACD_GOLDEN_CROSS -> ctx.macd() != null && ctx.macd().isGoldenCross();
            default -> false;
        };
    }

    /**
     * 수치형 지표 추출기
     */
    private double extractNumericIndicator(IndicatorType type, MarketContext ctx) {
        return switch (type) {
            // case STOCK_SCORE -> ctx.
            case MA5 -> ctx.ma5();
            case MA20 -> ctx.ma20();
            case MA50 -> ctx.ma50();
            case DIST_MA5 -> ctx.distFromMa5();
            case DIST_MA20 -> ctx.distFromMa20();
            case RSI -> ctx.rsi();
            case ADX -> ctx.adx();
            case ATR -> ctx.atr();
            case BAND_WIDTH -> ctx.bandWidth();
            case VOLUME -> ctx.currentVolume();
            default -> 0.0; // 잘못된 매핑 방어
        };
    }

}