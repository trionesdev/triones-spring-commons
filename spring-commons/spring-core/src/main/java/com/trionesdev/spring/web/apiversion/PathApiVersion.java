package com.trionesdev.spring.web.apiversion;

import org.springframework.web.bind.annotation.Mapping;
import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Mapping
public @interface PathApiVersion {
    String value() ;
}
