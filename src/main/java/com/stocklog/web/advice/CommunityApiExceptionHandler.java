package com.stocklog.web.advice;

import com.stocklog.global.error.BusinessException;
import com.stocklog.web.api.CommunityApiController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/**
 * 커뮤니티 API 전용 오류 응답.
 * GlobalExceptionHandler 는 오류 "화면"(error/404 등)을 돌려주는데, fetch 로 부르는 API 에 HTML 이 200 으로 가면
 * 화면에서 실패를 알 수 없다. 그래서 이 컨트롤러에 한해 상태 코드 + JSON 으로 돌려준다.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = CommunityApiController.class)
public class CommunityApiExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, String>> business(BusinessException e) {
        log.warn("[Community] {} {}", e.getErrorCode().getCode(), e.getMessage());
        return ResponseEntity.status(e.getErrorCode().getStatus())
                .body(Map.of("code", e.getErrorCode().getCode(), "message", e.getMessage()));
    }

    /** 좋아요를 동시에 두 번 눌러 uk_post_member 에 걸린 경우 등 — 이미 처리된 요청 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> conflict(DataIntegrityViolationException e) {
        log.info("[Community] 중복 요청 무시: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", "B409", "message", "이미 처리된 요청이에요. 새로고침해 주세요."));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> badRequest(Exception e) {
        return ResponseEntity.badRequest()
                .body(Map.of("code", "C001", "message", "요청 형식이 올바르지 않아요."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unknown(Exception e) {
        log.error("[Community] 처리하지 못한 오류", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("code", "C003", "message", "잠시 후 다시 시도해 주세요."));
    }
}
