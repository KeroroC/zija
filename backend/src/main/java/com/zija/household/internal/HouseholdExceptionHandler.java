package com.zija.household.internal;

import com.zija.ZijaRequestIdFilter;
import com.zija.household.internal.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = HouseholdController.class)
class HouseholdExceptionHandler {

    @ExceptionHandler(HouseholdAlreadyInitializedException.class)
    ProblemDetail handleAlreadyInitialized(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "家庭已初始化", "HOUSEHOLD_ALREADY_INITIALIZED");
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(HttpServletRequest request) {
        return problem(request, HttpStatus.UNAUTHORIZED, "未认证或账户未激活", "HOUSEHOLD_MEMBER_NOT_ACTIVE");
    }

    @ExceptionHandler(MemberConcurrentUpdateException.class)
    ProblemDetail handleMemberConcurrentUpdate(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT,
                "成员信息已被其他请求修改", "HOUSEHOLD_MEMBER_CONCURRENT_UPDATE");
    }

    @ExceptionHandler(InsufficientRoleException.class)
    ProblemDetail handleInsufficientRole(HttpServletRequest request) {
        return problem(request, HttpStatus.FORBIDDEN,
                "权限不足", "HOUSEHOLD_INSUFFICIENT_ROLE");
    }

    @ExceptionHandler(InvalidInvitationException.class)
    ProblemDetail handleInvalidToken(HttpServletRequest request) {
        return problem(request, HttpStatus.BAD_REQUEST,
                "链接无效或已过期", "HOUSEHOLD_TOKEN_INVALID");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleInvalidArgument(HttpServletRequest request) {
        return problem(request, HttpStatus.BAD_REQUEST,
                "请求参数无效", "HOUSEHOLD_REQUEST_INVALID");
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleStateConflict(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT,
                "当前状态不允许此操作", "HOUSEHOLD_STATE_CONFLICT");
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
