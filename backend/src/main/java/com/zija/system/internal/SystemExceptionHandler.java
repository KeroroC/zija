package com.zija.system.internal;

import com.zija.ZijaRequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class SystemExceptionHandler {

    @ExceptionHandler(SystemStateUnavailableException.class)
    ProblemDetail handleUnavailableState(
            SystemStateUnavailableException exception,
            HttpServletRequest request
    ) {
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
