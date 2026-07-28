package com.zija.inventory.internal.exception;

public class StocktakeNotDraftException extends RuntimeException {
    public StocktakeNotDraftException() { super(); }
    public StocktakeNotDraftException(String m) { super(m); }
}
