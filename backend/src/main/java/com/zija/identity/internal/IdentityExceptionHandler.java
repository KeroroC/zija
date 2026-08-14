package com.zija.identity.internal;

import com.zija.shared.ZijaProblems;
import com.zija.identity.internal.exception.AccountVersionConflictException;
import com.zija.identity.internal.exception.InvalidCredentialsException;
import com.zija.identity.internal.exception.LoginRateLimitedException;
import com.zija.identity.internal.exception.UsernameAlreadyExistsException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = IdentityController.class)
class IdentityExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(HttpServletRequest request) {
        return ZijaProblems.of(request, HttpStatus.UNAUTHORIZED, "用户名或密码错误", "AUTH_LOGIN_FAILED");
    }

    @ExceptionHandler(LoginRateLimitedException.class)
    ResponseEntity<ProblemDetail> handleRateLimited(HttpServletRequest request) {
        var body = ZijaProblems.of(request, HttpStatus.TOO_MANY_REQUESTS,
                "登录尝试过多", "AUTH_LOGIN_RATE_LIMITED");
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "300")
                .body(body);
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    ProblemDetail handleDuplicate(HttpServletRequest request) {
        return ZijaProblems.of(request, HttpStatus.CONFLICT, "用户名已存在", "IDENTITY_USERNAME_TAKEN");
    }

    @ExceptionHandler(AccountVersionConflictException.class)
    ProblemDetail handleVersionConflict(HttpServletRequest request) {
        return ZijaProblems.of(request, HttpStatus.CONFLICT, "版本冲突", "IDENTITY_VERSION_CONFLICT");
    }
}
