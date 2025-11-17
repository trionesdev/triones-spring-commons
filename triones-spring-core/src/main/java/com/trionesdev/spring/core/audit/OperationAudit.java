package com.trionesdev.spring.core.audit;

import org.intellij.lang.annotations.Language;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface OperationAudit {
    boolean batch() default false;
    /**
     * 类型
     *
     * @return
     */
    String type() default "";
    /**
     * 子域
     *
     * @return
     */
    String domain() default "";
    /**
     * 分类
     *
     * @return
     */
    String category() default "";

    /**
     * 行为
     *
     * @return
     */
    String action() default "";

    /**
     * 对象ID
     *
     * @return
     */
    @Language(value = "SpEL")
    String subject() default "";

    String subjectField() default "";

    /**
     * 描述
     *
     * @return
     */
    String description() default "";

    /**
     * 处理实现类
     *
     * @return
     */
    Class<?> handler() default Void.class;
}
