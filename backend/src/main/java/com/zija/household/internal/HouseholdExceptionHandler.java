package com.zija.household.internal;

import com.zija.shared.ZijaProblems;
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
        return ZijaProblems.of(request, HttpStatus.CONFLICT, "家庭已初始化", ErrorCodes.HOUSEHOLD_ALREADY_INITIALIZED);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(HttpServletRequest request) {
        return ZijaProblems.of(request, HttpStatus.UNAUTHORIZED, "未认证或账户未激活", ErrorCodes.HOUSEHOLD_MEMBER_NOT_ACTIVE);
    }

    @ExceptionHandler(MemberConcurrentUpdateException.class)
    ProblemDetail handleMemberConcurrentUpdate(HttpServletRequest request) {
        return ZijaProblems.of(request, HttpStatus.CONFLICT,
                "成员信息已被其他请求修改", ErrorCodes.HOUSEHOLD_MEMBER_CONCURRENT_UPDATE);
    }

    @ExceptionHandler(InsufficientRoleException.class)
    ProblemDetail handleInsufficientRole(HttpServletRequest request) {
        return ZijaProblems.of(request, HttpStatus.FORBIDDEN,
                "权限不足", ErrorCodes.HOUSEHOLD_INSUFFICIENT_ROLE);
    }

    @ExceptionHandler(InvalidInvitationException.class)
    ProblemDetail handleInvalidToken(HttpServletRequest request) {
        return ZijaProblems.of(request, HttpStatus.BAD_REQUEST,
                "链接无效或已过期", ErrorCodes.HOUSEHOLD_TOKEN_INVALID);
    }

    @ExceptionHandler(InvalidSetupTokenException.class)
    ProblemDetail handleInvalidSetupToken(HttpServletRequest request) {
        return ZijaProblems.of(request, HttpStatus.FORBIDDEN,
                "初始化口令无效", ErrorCodes.HOUSEHOLD_TOKEN_INVALID);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleInvalidArgument(HttpServletRequest request) {
        return ZijaProblems.of(request, HttpStatus.BAD_REQUEST,
                "请求参数无效", ErrorCodes.HOUSEHOLD_REQUEST_INVALID);
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleStateConflict(HttpServletRequest request) {
        return ZijaProblems.of(request, HttpStatus.CONFLICT,
                "当前状态不允许此操作", ErrorCodes.HOUSEHOLD_STATE_CONFLICT);
    }
}
