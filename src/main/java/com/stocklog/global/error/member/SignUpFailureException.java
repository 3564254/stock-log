package com.stocklog.global.error.member;

public class SignUpFailureException extends RuntimeException {
    public SignUpFailureException(String message) {
        super(message);
    }
}
