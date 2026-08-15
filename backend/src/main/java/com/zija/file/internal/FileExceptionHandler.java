package com.zija.file.internal;

import com.zija.shared.ZijaProblems;
import com.zija.file.exception.FileMediaTypeUnsupportedException;
import com.zija.file.exception.FileNameDuplicateException;
import com.zija.file.exception.FileNotAvailableException;
import com.zija.file.exception.FileSignatureMismatchException;
import com.zija.file.exception.FileTooLargeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 附件业务异常统一映射。
 * <p>
 * 不限定控制器类型：附件上传/改挂会经 catalog、inventory 的入口触发，
 * 同一套错误码（FILE_NAME_DUPLICATE 等）必须对所有入口稳定生效。
 */
@RestControllerAdvice
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

    @ExceptionHandler(FileNotAvailableException.class)
    ProblemDetail handleNotAvailable(HttpServletRequest request, FileNotAvailableException ex) {
        return ZijaProblems.of(request, HttpStatus.CONFLICT, "附件处于回收站，请先恢复", ErrorCodes.FILE_NOT_AVAILABLE);
    }
}
