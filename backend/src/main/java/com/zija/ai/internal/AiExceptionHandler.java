package com.zija.ai.internal;

import com.zija.ai.internal.exception.AiConfigurationVersionConflictException;
import com.zija.shared.ZijaProblems;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AiConfigurationController.class)
class AiExceptionHandler {

    @ExceptionHandler(AiConfigurationVersionConflictException.class)
    ProblemDetail handleVersionConflict(HttpServletRequest request) {
        return ZijaProblems.of(request, HttpStatus.CONFLICT, "AI 配置版本冲突",
                ErrorCodes.CONFIGURATION_VERSION_CONFLICT);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleInvalidArgument(HttpServletRequest request, IllegalArgumentException exception) {
        return ZijaProblems.of(request, HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage(),
                ErrorCodes.INVALID_CONFIGURATION);
    }
}
