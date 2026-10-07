package com.stocklog.global.aop;

import com.stocklog.modules.stock.application.request.StockRequest;
import com.stocklog.modules.stock.chart.dto.ChartRequest;
import com.stocklog.modules.stock.domain.type.DataType;
import com.stocklog.modules.common.type.Period;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.util.Arrays;

@Aspect
@Slf4j
@Component
public class LoggingAspect {

    private static final long SLOW_THRESHOLD_MS = 1500L; // 1.5초 이상 지연 시 경고

    @Before(value = "execution(* com.stocklog.modules..*Provider.fetch*(..)) && args(request, period,..)", argNames = "joinPoint,request,period")
    public void logBeforeFetchChart(JoinPoint joinPoint, ChartRequest request, Period period) {
        if (request == null) {
            log.warn("[Chart] request is null. period={}", period);
            return;
        }
        log.info("[Chart] code={}, period={}, date={}", request.code(), period, request.localDate());
    }

    @Around("providerMethods()")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String dataName = DataType.getType(className);
        Object[] args = joinPoint.getArgs();
        String code = "";

        if (args.length >= 1) {
            if (args[0] instanceof ChartRequest request) {
                code = request.code();
            } else if (args[0] instanceof StockRequest request) {
                code = request.code();
            }
        }


        try {
            Object result = joinPoint.proceed();
            stopWatch.stop();
            long executionTime = stopWatch.getTotalTimeMillis();

            if (executionTime >= SLOW_THRESHOLD_MS) {
                log.warn("[{}][{}] 지연 경고: {}.{}() - 소요: {}ms", dataName, code, className, methodName, executionTime);
            } else {
                log.info("[{}][{}] 성공: {}.{}() - 소요: {}ms", dataName, code, className, methodName, executionTime);
            }
            return result;
        } catch (Throwable throwable) {
            stopWatch.stop();
            log.error("[{}][{}] 에러 발생: {}.{}() -> 메시지: {}", dataName, code, className, methodName, throwable.getMessage(), throwable);
            throw throwable;
        }
    }

    @Pointcut("execution(* com.stocklog.modules..*Provider.fetch*(..))")
    private void providerMethods(){}

    @Pointcut("@annotation(com.stocklog.global.annotation.LogChartFetch)")
    private void logChartFetch(){}






}
