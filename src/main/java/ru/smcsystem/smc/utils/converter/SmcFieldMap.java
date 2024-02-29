package ru.smcsystem.smc.utils.converter;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target({FIELD, METHOD, PARAMETER})
public @interface SmcFieldMap {
    String key() default "key";

    String value() default "value";

    boolean ignoreCase() default false;
}
