package com.market.payload.validation;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

/**
 * A password of 8 to 72 characters that fits in 72 UTF-8 bytes: BCrypt ignores everything after byte 72, so a longer
 * password would be silently truncated.
 */
@Documented
@Constraint(validatedBy = BcryptPassword.Validator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BcryptPassword {

    String message() default "must be 8 to 72 characters (at most 72 bytes)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<BcryptPassword, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            // Null/blank is reported by @NotBlank.
            return value == null || (value.length() >= 8 && value.getBytes(StandardCharsets.UTF_8).length <= 72);
        }
    }
}
