package com.zija.household.internal;

import com.zija.ZijaRequestIdFilter;
import com.zija.household.internal.exception.HouseholdAlreadyInitializedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {HouseholdController.class})
class HouseholdExceptionHandler {

    @ExceptionHandler(HouseholdAlreadyInitializedException.class)
    ProblemDetail handleAlreadyInitialized(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "家庭已初始化", "HOUSEHOLD_ALREADY_INITIALIZED");
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(HttpServletRequest request) {
        return problem(request, HttpStatus.UNAUTHORIZED, "未认证或账户未激活", "HOUSEHOLD_MEMBER_NOT_ACTIVE");
    }

    private ProblemDetail problem(HttpServletRequest request, HttpStatus status,
                                   String title, String errorCode) {
        var problem = ProblemDetail.forStatusAndDetail(status, title);
        problem.setTitle(title);
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("requestId",
                request.getAttribute(ZijaRequestIdFilter.ATTRIBUTE));
        return problem;
    }
}
