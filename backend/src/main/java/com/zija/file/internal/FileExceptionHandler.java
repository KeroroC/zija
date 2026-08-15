package com.zija.file.internal;

import com.zija.shared.ZijaProblems;
import com.zija.file.internal.exception.FileMediaTypeUnsupportedException;
import com.zija.file.internal.exception.FileNameDuplicateException;
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
        return ZijaProblems.of(request, HttpStatus.CONTENT_TOO_LARGE, "文件过大", ErrorCodes.FILE_TOO_LARGE);
    }

    @ExceptionHandler(FileMediaTypeUnsupportedException.class)
    ProblemDetail handleUnsupported(HttpServletRequest request, FileMediaTypeUnsupportedException ex) {
        return ZijaProblems.of(request, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持的文件类型", ErrorCodes.FILE_MEDIA_TYPE_UNSUPPORTED);
    }

    @ExceptionHandler(FileSignatureMismatchException.class)
    ProblemDetail handleMismatch(HttpServletRequest request, FileSignatureMismatchException ex) {
        return ZijaProblems.of(request, HttpStatus.UNPROCESSABLE_CONTENT, "文件签名不匹配", ErrorCodes.FILE_SIGNATURE_MISMATCH);
    }

    @ExceptionHandler(FileNameDuplicateException.class)
    ProblemDetail handleDuplicateName(HttpServletRequest request, FileNameDuplicateException ex) {
        return ZijaProblems.of(request, HttpStatus.CONFLICT, "同一挂载点下附件名字不可重复", ErrorCodes.FILE_NAME_DUPLICATE);
    }
}
