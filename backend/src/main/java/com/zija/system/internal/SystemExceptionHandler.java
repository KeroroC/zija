package com.zija.system.internal;

import com.zija.ZijaRequestIdFilter;
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
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "The system installation state could not be loaded."
        );
        problem.setTitle("System state unavailable");
        problem.setProperty(
                "errorCode",
                "system_state_unavailable"
        );
        problem.setProperty(
                "requestId",
                request.getAttribute(ZijaRequestIdFilter.ATTRIBUTE)
        );
        return problem;
    }
}
