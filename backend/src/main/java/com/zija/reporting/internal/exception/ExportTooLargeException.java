package com.zija.reporting.internal.exception;

/**
 * 导出行数超限异常。当实际行数超过 MAX_ROWS 时抛出。
 */
public class ExportTooLargeException extends RuntimeException {
    private final int actualRows;
    private final int maxRows;

    public ExportTooLargeException(int actualRows, int maxRows) {
        super("Export too large: " + actualRows + " rows (max " + maxRows + ")");
        this.actualRows = actualRows;
        this.maxRows = maxRows;
    }

    public int getActualRows() { return actualRows; }
    public int getMaxRows() { return maxRows; }
}
