package com.zija.inventory.internal.exception;

public class StocktakeStaleException extends RuntimeException {
    public StocktakeStaleException() { super(); }
    public StocktakeStaleException(String m) { super(m); }
}
