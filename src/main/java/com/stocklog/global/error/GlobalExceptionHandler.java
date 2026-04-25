package com.stocklog.global.error;

import com.stocklog.global.error.member.LoginFailureException;
import com.stocklog.global.error.member.SignUpFailureException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public String handleNotFound(EntityNotFoundException e) {
        return "error/404";
    }
}
