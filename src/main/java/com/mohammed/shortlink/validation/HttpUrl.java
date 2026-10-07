package com.mohammed.shortlink.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = HttpUrlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpUrl {
    String message() default "must be an absolute HTTP or HTTPS URL with a valid host and port";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
