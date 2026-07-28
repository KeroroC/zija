package com.zija.file.internal;

import com.zija.file.internal.exception.FileMediaTypeUnsupportedException;
import com.zija.file.internal.exception.FileSignatureMismatchException;
import com.zija.file.internal.exception.FileTooLargeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = FileController.class)
class FileExceptionHandler {

    @ExceptionHandler(FileTooLargeException.class)
    ProblemDetail handleTooLarge(HttpServletRequest request, FileTooLargeException ex) {
        return problem(request, HttpStatus.PAYLOAD_TOO_LARGE, "文件过大", "FILE_TOO_LARGE");
    }

    @ExceptionHandler(FileMediaTypeUnsupportedException.class)
    ProblemDetail handleUnsupported(HttpServletRequest request, FileMediaTypeUnsupportedException ex) {
        return problem(request, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持的文件类型", "FILE_MEDIA_TYPE_UNSUPPORTED");
    }

    @ExceptionHandler(FileSignatureMismatchException.class)
    ProblemDetail handleMismatch(HttpServletRequest request, FileSignatureMismatchException ex) {
        return problem(request, HttpStatus.UNPROCESSABLE_ENTITY, "文件签名不匹配", "FILE_SIGNATURE_MISMATCH");
    }

    private ProblemDetail problem(HttpServletRequest request, HttpStatus status, String title, String errorCode) {
        var problem = ProblemDetail.forStatusAndDetail(status, title);
        problem.setTitle(title);
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("requestId", request.getAttribute("zija.request-id"));
        return problem;
    }
}
