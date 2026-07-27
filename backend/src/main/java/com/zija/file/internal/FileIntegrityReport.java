package com.zija.file.internal;

import java.util.List;

public record FileIntegrityReport(
    long checkedCount,
    long missingCount,
    long hashMismatchCount,
    long byteSizeMismatchCount,
    long orphanCount,
    List<String> missing,
    List<String> hashMismatch) {}
