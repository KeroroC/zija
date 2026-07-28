package com.zija.inventory.internal;

import com.zija.inventory.internal.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {InventoryController.class})
class InventoryExceptionHandler {

    @ExceptionHandler(InventoryInsufficientStockException.class)
    ProblemDetail handleInsufficientStock(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "库存不足", "INVENTORY_INSUFFICIENT_STOCK");
    }

    @ExceptionHandler(InventoryQuantityPrecisionInvalidException.class)
    ProblemDetail handleQuantityPrecisionInvalid(HttpServletRequest request) {
        return problem(request, HttpStatus.UNPROCESSABLE_ENTITY, "数量精度超过单位允许范围", "INVENTORY_QUANTITY_PRECISION_INVALID");
    }

    @ExceptionHandler(InventoryIdempotencyConflictException.class)
    ProblemDetail handleIdempotencyConflict(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "幂等键冲突", "INVENTORY_IDEMPOTENCY_CONFLICT");
    }

    @ExceptionHandler(InventoryArchivedItemException.class)
    ProblemDetail handleArchivedItem(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "归档物品不可操作", "INVENTORY_ARCHIVED_ITEM");
    }

    @ExceptionHandler(InventoryLotVersionConflictException.class)
    ProblemDetail handleLotVersionConflict(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "批次版本冲突", "INVENTORY_LOT_VERSION_CONFLICT");
    }

    @ExceptionHandler(InventoryLotNotFoundException.class)
    ProblemDetail handleLotNotFound(HttpServletRequest request) {
        return problem(request, HttpStatus.NOT_FOUND, "批次不存在", "INVENTORY_LOT_NOT_FOUND");
    }

    @ExceptionHandler(InventoryMovementAlreadyReversedException.class)
    ProblemDetail handleMovementAlreadyReversed(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "流水已撤销", "INVENTORY_MOVEMENT_ALREADY_REVERSED");
    }

    @ExceptionHandler(InventoryReversalNotAllowedException.class)
    ProblemDetail handleReversalNotAllowed(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "该类型流水不允许撤销", "INVENTORY_REVERSAL_NOT_ALLOWED");
    }

    @ExceptionHandler(InventoryReversalWouldNegativeException.class)
    ProblemDetail handleReversalWouldNegative(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "撤销会导致库存为负", "INVENTORY_REVERSAL_WOULD_NEGATIVE");
    }

    @ExceptionHandler(StocktakeStaleException.class)
    ProblemDetail handleStocktakeStale(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "盘点范围内库存已变化", "INVENTORY_STOCKTAKE_STALE");
    }

    @ExceptionHandler(StocktakeNotDraftException.class)
    ProblemDetail handleStocktakeNotDraft(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "盘点单不是草稿状态", "INVENTORY_STOCKTAKE_NOT_DRAFT");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(HttpServletRequest request) {
        return problem(request, HttpStatus.BAD_REQUEST, "请求字段校验失败", "VALIDATION_FAILED");
    }

    private ProblemDetail problem(HttpServletRequest request, HttpStatus status, String title, String errorCode) {
        var problem = ProblemDetail.forStatusAndDetail(status, title);
        problem.setTitle(title);
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("requestId", request.getAttribute("zija.request-id"));
        return problem;
    }
}
