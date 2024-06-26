package com.trionesdev.spring.core.event.act;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Objects;

@RequiredArgsConstructor
@Aspect
public class ActEventAroundAspect extends ActEventAspect {

    @Pointcut("@annotation(com.trionesdev.spring.core.event.act.ActEventAround)")
    public void actEventAround() {
    }

    @Around(value = "actEventAround()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Signature signature = joinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        ActEventAround actEventAround = AnnotationUtils.getAnnotation(methodSignature.getMethod(), ActEventAround.class);
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = null;
        if (Objects.nonNull(actEventAround)) {
            context = new ActEventEvaluationContext(joinPoint.getTarget(), methodSignature.getMethod(), joinPoint.getArgs(), new DefaultParameterNameDiscoverer());
            context.setBeanResolver(this.beanResolver);
            if (StringUtils.isNoneBlank(actEventAround.before())) {
                Object beforeResult = parser.parseExpression(actEventAround.before()).getValue(context);
                context.setVariable("beforeData", beforeResult);
            }
        }
        Object result = joinPoint.proceed();
        if (Objects.nonNull(actEventAround)) {
            parser.parseExpression(actEventAround.after()).getValue(context);
        }
        return result;
    }

}
