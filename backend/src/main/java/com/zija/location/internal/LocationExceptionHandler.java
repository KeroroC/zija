package com.zija.location.internal;

import com.zija.location.internal.exception.LocationCycleException;
import com.zija.location.internal.exception.LocationHasChildrenException;
import com.zija.location.internal.exception.LocationReferencedException;
import com.zija.location.internal.exception.LocationVersionConflictException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = LocationController.class)
class LocationExceptionHandler {

    @ExceptionHandler(LocationVersionConflictException.class)
    ProblemDetail handleVersionConflict(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "版本冲突", "LOCATION_VERSION_CONFLICT");
    }

    @ExceptionHandler(LocationCycleException.class)
    ProblemDetail handleCycle(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "移动会导致循环", "LOCATION_CYCLE");
    }

    @ExceptionHandler(LocationHasChildrenException.class)
    ProblemDetail handleHasChildren(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "位置包含子节点", "LOCATION_HAS_CHILDREN");
    }

    @ExceptionHandler(LocationReferencedException.class)
    ProblemDetail handleReferenced(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "位置已被库存引用", "LOCATION_REFERENCED");
    }

    private ProblemDetail problem(HttpServletRequest request, HttpStatus status, String title, String errorCode) {
        var problem = ProblemDetail.forStatusAndDetail(status, title);
        problem.setTitle(title);
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("requestId", request.getAttribute("zija.request-id"));
        return problem;
    }
}
