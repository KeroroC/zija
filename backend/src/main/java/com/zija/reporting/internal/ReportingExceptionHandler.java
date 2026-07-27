package com.zija.reporting.internal;

import com.zija.reporting.internal.export.ExportTooLargeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(assignableTypes = ReportingController.class)
class ReportingExceptionHandler {

    @ExceptionHandler(ExportTooLargeException.class)
    ResponseEntity<Map<String, Object>> handleExportTooLarge(ExportTooLargeException ex) {
        var body = new LinkedHashMap<String, Object>();
        body.put("type", "about:blank");
        body.put("title", "Export Too Large");
        body.put("status", 400);
        body.put("errorCode", "REPORTING_EXPORT_TOO_LARGE");
        body.put("detail", "Export contains " + ex.getActualRows() + " rows (max " + ex.getMaxRows() + "). "
                + "Please narrow your filter criteria.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
