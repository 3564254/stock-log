package com.stocklog.global.error;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanIsAbstractException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(BusinessException e, Model model) {
        log.warn("[BusinessException] code: {}, message: {}", e.getErrorCode().getCode(), e.getMessage());
        model.addAttribute("errorMessage", e.getMessage());
        HttpStatus status = e.getErrorCode().getStatus();

        // 1. 404 Not Found 계열 (예: MEMBER_NOT_FOUND, STOCK_NOT_FOUND 등)
        if (status == HttpStatus.NOT_FOUND) {
            return "error/404";
        }

        // 2. 403 Forbidden 계열 (예: FORBIDDEN_ACCESS)
        if (status == HttpStatus.FORBIDDEN) {
            return "error/403";
        }

        // 3. 그 외 (500 서버 에러 및 기타)
        return "error/500";
    }

    @ExceptionHandler(Exception.class)
    public String handleException(Exception e, Model model) {
        log.error("[Uncaught Exception] ", e);
        model.addAttribute("errorMessage", "서버 내부에서 알 수 없는 오류가 발생했습니다.");
        return "error/500";
    }
}
