package com.zija.catalog.internal;

import com.zija.shared.ZijaProblems;
import com.zija.catalog.internal.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {CatalogDictionaryController.class, ItemController.class})
class CatalogExceptionHandler {

    @ExceptionHandler(CatalogVersionConflictException.class)
    ProblemDetail handleVersionConflict(HttpServletRequest request) {
        return ZijaProblems.of(request, HttpStatus.CONFLICT, "版本冲突", ErrorCodes.CATALOG_VERSION_CONFLICT);
    }

    @ExceptionHandler(CatalogDictionaryNameExistsException.class)
    ProblemDetail handleNameExists(HttpServletRequest request) {
        return ZijaProblems.of(request, HttpStatus.CONFLICT, "名称已存在", ErrorCodes.CATALOG_DICTIONARY_NAME_EXISTS);
    }

    @ExceptionHandler(CatalogCategoryHasChildrenException.class)
    ProblemDetail handleHasChildren(HttpServletRequest request) {
        return ZijaProblems.of(request, HttpStatus.CONFLICT, "分类包含活动子分类", ErrorCodes.CATALOG_CATEGORY_HAS_CHILDREN);
    }

    @ExceptionHandler(CatalogUnitPrecisionLockedException.class)
    ProblemDetail handlePrecisionLocked(HttpServletRequest request) {
        return ZijaProblems.of(request, HttpStatus.CONFLICT, "单位精度已被物品锁定", ErrorCodes.CATALOG_UNIT_PRECISION_LOCKED);
    }

    @ExceptionHandler(CatalogUnitPrecisionInvalidException.class)
    ProblemDetail handlePrecisionInvalid(HttpServletRequest request) {
        return ZijaProblems.of(request, HttpStatus.UNPROCESSABLE_CONTENT, "阈值精度超过单位允许范围", ErrorCodes.CATALOG_UNIT_PRECISION_INVALID);
    }

    @ExceptionHandler(CatalogArchivedDictionaryException.class)
    ProblemDetail handleArchivedDictionary(HttpServletRequest request) {
        return ZijaProblems.of(request, HttpStatus.CONFLICT, "归档的字典项不可使用", ErrorCodes.CATALOG_ARCHIVED_DICTIONARY);
    }

    @ExceptionHandler(CatalogCycleDetectedException.class)
    ProblemDetail handleCycleDetected(HttpServletRequest request) {
        return ZijaProblems.of(request, HttpStatus.CONFLICT, "移动会产生循环引用", ErrorCodes.CATALOG_CYCLE_DETECTED);
    }
}
