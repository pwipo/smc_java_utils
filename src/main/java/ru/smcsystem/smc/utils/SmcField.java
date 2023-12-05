package ru.smcsystem.smc.utils;

import ru.smcsystem.smc.utils.converter.SmcConverter;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target({FIELD, METHOD, PARAMETER})
public @interface SmcField {
    /**
     * If the value is "##default", then element name is derived from the JavaBean property name.
     */
    String name() default "##default";

    /**
     * Customize the element declaration to be required.
     */
    boolean required() default false;

    /**
     * converter
     */
    Class<? extends SmcConverter> converter() default SmcConverter.None.class;

    /**
     * order, ascending sorting is used.
     * if negative or 0, then not use.
     */
    int order() default 0;

}
