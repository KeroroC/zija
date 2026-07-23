package com.zija;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

/**
 * {@link Utf8ByteLength} 注解的校验器实现。
 * <p>
 * 将输入值转为 UTF-8 字节数组后判断长度是否超过注解指定的上限。
 * {@code null} 值视为合法（交由 {@code @NotNull} 等注解处理空值校验）。
 */
public class Utf8ByteLengthValidator
        implements ConstraintValidator<Utf8ByteLength, CharSequence> {

    private int max;

    /** 从注解中读取最大字节数配置。 */
    @Override
    public void initialize(Utf8ByteLength annotation) {
        this.max = annotation.max();
    }

    /** 校验值的 UTF-8 字节长度是否在允许范围内，null 值视为合法。 */
    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        return value == null
                || value.toString().getBytes(StandardCharsets.UTF_8).length <= max;
    }
}
