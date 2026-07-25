package com.zija.inventory.internal;

public class StocktakeNotDraftException extends RuntimeException {
    public StocktakeNotDraftException() { super(); }
    public StocktakeNotDraftException(String m) { super(m); }
}
