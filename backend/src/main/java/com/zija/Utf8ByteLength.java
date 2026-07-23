package com.zija;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * UTF-8 字节长度校验注解。
 * <p>
 * 用于约束字符串字段/参数的 UTF-8 编码后的字节长度不超过指定上限，
 * 适用于需要与数据库字段长度（按字节计算）对齐的场景。
 * 由 {@link Utf8ByteLengthValidator} 执行实际校验。
 */
@Documented
@Constraint(validatedBy = Utf8ByteLengthValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER,
        ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Utf8ByteLength {

    String message() default "UTF-8 编码长度不能超过 {max} 字节";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** 允许的最大 UTF-8 字节数。 */
    int max();
}
