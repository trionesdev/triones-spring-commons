package com.trionesdev.spring.lock;

import org.intellij.lang.annotations.Language;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface Lock {
    @Language(value = "SpEL")
    String key();

    long waitTime() default 0;

    long leaseTime() default 0;

    TimeUnit unit() default TimeUnit.MILLISECONDS;
}
