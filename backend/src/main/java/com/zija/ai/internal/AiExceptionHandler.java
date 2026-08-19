package com.zija.ai.internal;

import com.zija.ai.internal.exception.AiConfigurationVersionConflictException;
import com.zija.ai.internal.exception.KnowledgeSourceFormatUnsupportedException;
import com.zija.ai.internal.exception.KnowledgeSourceNotFoundException;
import com.zija.ai.internal.exception.KnowledgeSourceStateConflictException;
import com.zija.shared.ZijaProblems;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        AiConfigurationController.class,
        KnowledgeSourceController.class,
        HouseholdFactQaController.class
})
class AiExceptionHandler {

    @ExceptionHandler(AiConfigurationVersionConflictException.class)
    ProblemDetail handleVersionConflict(HttpServletRequest request) {
        return ZijaProblems.of(request, HttpStatus.CONFLICT, "AI 配置版本冲突",
                ErrorCodes.CONFIGURATION_VERSION_CONFLICT);
    }

    @ExceptionHandler(KnowledgeSourceNotFoundException.class)
    ProblemDetail handleKnowledgeSourceNotFound(
            HttpServletRequest request,
            KnowledgeSourceNotFoundException exception
    ) {
        return ZijaProblems.of(request, HttpStatus.NOT_FOUND, exception.getMessage(),
                ErrorCodes.KNOWLEDGE_SOURCE_NOT_FOUND);
    }

    @ExceptionHandler(KnowledgeSourceFormatUnsupportedException.class)
    ProblemDetail handleKnowledgeSourceFormatUnsupported(
            HttpServletRequest request,
            KnowledgeSourceFormatUnsupportedException exception
    ) {
        return ZijaProblems.of(request, HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage(),
                ErrorCodes.KNOWLEDGE_SOURCE_FORMAT_UNSUPPORTED);
    }

    @ExceptionHandler(KnowledgeSourceStateConflictException.class)
    ProblemDetail handleKnowledgeSourceStateConflict(
            HttpServletRequest request,
            KnowledgeSourceStateConflictException exception
    ) {
        return ZijaProblems.of(request, HttpStatus.CONFLICT, exception.getMessage(),
                ErrorCodes.KNOWLEDGE_SOURCE_STATE_CONFLICT);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleInvalidArgument(HttpServletRequest request, IllegalArgumentException exception) {
        return ZijaProblems.of(request, HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage(),
                ErrorCodes.INVALID_CONFIGURATION);
    }

    @ExceptionHandler(AiRequestLimitException.class)
    ResponseEntity<ProblemDetail> handleRequestLimit(
            HttpServletRequest request,
            AiRequestLimitException exception
    ) {
        HttpStatus status = "AI_CONTEXT_LIMIT_EXCEEDED".equals(exception.reasonCode())
                ? HttpStatus.PAYLOAD_TOO_LARGE
                : HttpStatus.TOO_MANY_REQUESTS;
        ProblemDetail problem = ZijaProblems.of(
                request, status, "AI 请求受限", exception.getMessage(), ErrorCodes.REQUEST_LIMITED);
        problem.setProperty("reasonCode", exception.reasonCode());
        return ResponseEntity.status(status).body(problem);
    }
}
