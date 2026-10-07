package com.stocklog.global.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    // ── 공통 (Global) ──
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력값"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C002", "지원하지 않는 HTTP 메서드"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C003", "서버 내부 오류"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "C004", "리소스를 찾을 수 없음"),

    // ── 인증 및 권한 (Auth) ──
    UNAUTHORIZED_ACCESS(HttpStatus.UNAUTHORIZED, "A001", "인증되지 않은 사용자"),
    FORBIDDEN_ACCESS(HttpStatus.FORBIDDEN, "A002", "접근 권한이 없음"),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "A003", "비밀번호가 일치하지 않음"),
    LOGIN_FAILURE(HttpStatus.UNAUTHORIZED, "A004", "아이디 또는 비밀번호 확인 요망"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "A005", "만료된 인증 토큰"),

    // ── 회원 (Member) ──
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "M001", "존재하지 않는 회원"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "M002", "이미 사용 중인 이메일"),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "M003", "이미 사용 중인 아이디"),

    // ── 주식 및 외부 API (Stock) ──
    STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "S001", "존재하지 않는 종목 코드"),
    INDEX_NOT_FOUND(HttpStatus.NOT_FOUND, "S002", "존재하지 않는 지수 코드"),
    MARKET_NOT_FOUND(HttpStatus.NOT_FOUND,"S003","존재하지 않는 시장 코드"),
    NEWS_NOT_FOUND(HttpStatus.NOT_FOUND,"S004","새로운 뉴스를 찾을 수 없음"),
    EXTERNAL_API_TIMEOUT(HttpStatus.SERVICE_UNAVAILABLE, "S005", "외부 증권사 API 응답 지연"),
    EXTERNAL_API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "S006", "외부 증권사 API 연동 오류"),

    // ── 포트폴리오 (Portfolio) ──
    PORTFOLIO_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "포트폴리오 정보를 찾을 수 없음"),
    INSUFFICIENT_QUANTITY(HttpStatus.BAD_REQUEST, "P002", "보유 수량보다 많은 수량 차감 불가"),
    INVALID_PURCHASE_PRICE(HttpStatus.BAD_REQUEST, "P003", "매수 평단가는 0보다 커야 함"),

    // ── 트레이드 및 관심종목 (Trade) ──
    WATCH_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "T001", "관심 종목 대기열에서 해당 종목을 찾을 수 없음"),
    DUPLICATE_WATCH_ITEM(HttpStatus.CONFLICT, "T002", "이미 관심 종목 대기열에 등록된 주식"),
    TRADING_RULE_NOT_FOUND(HttpStatus.NOT_FOUND, "T003", "해당 매매 규칙을 찾을 수 없음");

    private final HttpStatus status;
    private final String code;
    private final String message;
}