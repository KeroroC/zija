package com.zija.catalog.internal;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {CatalogDictionaryController.class, ItemController.class})
class CatalogExceptionHandler {

    @ExceptionHandler(CatalogVersionConflictException.class)
    ProblemDetail handleVersionConflict(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "版本冲突", "CATALOG_VERSION_CONFLICT");
    }

    @ExceptionHandler(CatalogDictionaryNameExistsException.class)
    ProblemDetail handleNameExists(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "名称已存在", "CATALOG_DICTIONARY_NAME_EXISTS");
    }

    @ExceptionHandler(CatalogCategoryHasChildrenException.class)
    ProblemDetail handleHasChildren(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "分类包含活动子分类", "CATALOG_CATEGORY_HAS_CHILDREN");
    }

    @ExceptionHandler(CatalogUnitPrecisionLockedException.class)
    ProblemDetail handlePrecisionLocked(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "单位精度已被物品锁定", "CATALOG_UNIT_PRECISION_LOCKED");
    }

    @ExceptionHandler(CatalogUnitPrecisionInvalidException.class)
    ProblemDetail handlePrecisionInvalid(HttpServletRequest request) {
        return problem(request, HttpStatus.UNPROCESSABLE_ENTITY, "阈值精度超过单位允许范围", "CATALOG_UNIT_PRECISION_INVALID");
    }

    @ExceptionHandler(CatalogArchivedDictionaryException.class)
    ProblemDetail handleArchivedDictionary(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "归档的字典项不可使用", "CATALOG_ARCHIVED_DICTIONARY");
    }

    private ProblemDetail problem(HttpServletRequest request, HttpStatus status, String title, String errorCode) {
        var problem = ProblemDetail.forStatusAndDetail(status, title);
        problem.setTitle(title);
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("requestId", request.getAttribute("zija.request-id"));
        return problem;
    }
}
