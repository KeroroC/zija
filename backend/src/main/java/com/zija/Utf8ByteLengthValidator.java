package com.zija;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class Utf8ByteLengthValidator
        implements ConstraintValidator<Utf8ByteLength, CharSequence> {

    private int max;

    @Override
    public void initialize(Utf8ByteLength annotation) {
        this.max = annotation.max();
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        return value == null
                || value.toString().getBytes(StandardCharsets.UTF_8).length <= max;
    }
}
