package com.stocklog.global.aop;

import com.stocklog.modules.market.domain.type.IndexType;
import com.stocklog.modules.stock.chart.dto.ChartRequest;
import com.stocklog.modules.stock.application.request.StockRequest;
import com.stocklog.modules.stock.domain.type.DataType;
import com.stocklog.modules.common.type.Period;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Slf4j
@Component
public class LoggingAspect {

    @Before("execution(* com.stocklog.modules..*Provider.fetch*(..)) && args(request, period)")
    public void logBeforeFetchChart(JoinPoint joinPoint, ChartRequest request, Period period) {
        log.info("[Chart] code={}, period={}, date={}", request.code(), period, request.localDate());
    }

    @Before("execution(* com.stocklog.modules.stock..*Provider.fetch*(..)) && args(request,..)")
    public void logBeforeFetchPrice(JoinPoint joinPoint, StockRequest request) {
        log.info("[Price][{}]", request.code());
    }

    @Before("execution(* com.stocklog.modules.ai..*AiProvider.get*(..))")
    public void logBeforeAiProviderExecution(JoinPoint joinPoint) {
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        log.info("[AI API] 호출 시작: {}.{}()", className, methodName);
    }

    @Before("execution(* com.stocklog.modules.market..*Provider.fetch*(..)) && args(type,..)")
    public void logBeforeFetchIndexExecution(JoinPoint joinPoint, IndexType type) {
        log.info("[Index][{}]: 최신 데이터 호출", type.getSeriesId());
    }

    @Before("execution(* com.stocklog.modules.market.news..*Provider.fetch*(..)) && args(request,..)")
    public void logBeforeFetchStockNewsExecution(JoinPoint joinPoint, StockRequest request) {
        log.info("[News][{}]: 최신 데이터 호출", request.code());
    }


    @Around("(execution(* com.stocklog.modules..*Provider.fetch*(..)) || execution(* com.stocklog.modules..*Provider.get*(..)))")
    public Object logProviderExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String dataName = DataType.getType(className);

        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            log.error("[{}] 에러 발생: {}.{}() -> 메시지: {}", dataName, className, methodName, throwable.getMessage(), throwable);
            throw throwable;
        } finally {
            long totalTime = System.currentTimeMillis() - startTime;
            log.info("[{}] 정상 종료: {}.{}() - 소요시간: {}ms", dataName, className, methodName, totalTime);
        }
    }



}
