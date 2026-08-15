package com.zija.household.internal;

import com.zija.household.internal.exception.InvalidCredentialsException;
import com.zija.shared.ZijaProblems;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 成员授权失败的全局映射。
 * <p>
 * {@code HouseholdApi.requireActiveMember} 会被其它模块的控制器（file/catalog/inventory 等）
 * 调用：非成员 / 停用成员必须对任何入口稳定返回 401，而不是 500。
 * 只处理本类异常，避免与各模块自身的全局 advice 冲突。</p>
 */
@RestControllerAdvice
class HouseholdAuthzExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(HttpServletRequest request) {
        return ZijaProblems.of(request, HttpStatus.UNAUTHORIZED,
                "未认证或账户未激活", ErrorCodes.HOUSEHOLD_MEMBER_NOT_ACTIVE);
    }
}
