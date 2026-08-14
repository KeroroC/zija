package com.zija.system.internal;

import com.zija.shared.ZijaProblems;
import com.zija.system.internal.exception.SystemStateUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;

@RestControllerAdvice(assignableTypes = SystemController.class)
class SystemExceptionHandler {

    @ExceptionHandler(SystemStateUnavailableException.class)
    ProblemDetail handleUnavailableState(
            SystemStateUnavailableException exception,
            HttpServletRequest request
    ) {
        return unavailableProblem(request);
    }

    @ExceptionHandler({
            DataAccessException.class,
            TransactionException.class
    })
    ProblemDetail handleDatabaseFailure(HttpServletRequest request) {
        return unavailableProblem(request);
    }

    private ProblemDetail unavailableProblem(HttpServletRequest request) {
        return ZijaProblems.of(request, HttpStatus.INTERNAL_SERVER_ERROR,
                "System state unavailable",
                "The system installation state could not be loaded.",
                "system_state_unavailable");
    }
}
