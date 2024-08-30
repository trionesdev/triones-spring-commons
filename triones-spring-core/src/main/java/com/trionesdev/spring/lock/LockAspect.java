package com.trionesdev.spring.lock;

import com.trionesdev.commons.lock.TrionesLock;
import com.trionesdev.commons.lock.TrionesLockTemplate;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Objects;

@RequiredArgsConstructor
@Aspect
public class LockAspect implements ApplicationContextAware {
    private final TrionesLockTemplate trionesLockTemplate;
    protected BeanResolver beanResolver;

    @Pointcut(value = "@annotation(com.trionesdev.spring.lock.Lock)")
    public void lockAspect() {
    }

    @Around(value = "lockAspect()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Signature signature = joinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        Lock trionesLock = AnnotationUtils.getAnnotation(methodSignature.getMethod(), Lock.class);

        Object result = null;
        if (Objects.nonNull(trionesLock)) {
            ExpressionParser parser = new SpelExpressionParser();
            StandardEvaluationContext context = new MethodBasedEvaluationContext(joinPoint.getTarget(), methodSignature.getMethod(), joinPoint.getArgs(), new DefaultParameterNameDiscoverer());
            context.setBeanResolver(this.beanResolver);
            String keyValue = String.valueOf(parser.parseExpression(trionesLock.key()).getValue(context));
            TrionesLock lock = trionesLockTemplate.getLock(keyValue);
            try {
                boolean locked = lock.tryLock(trionesLock.waitTime(), trionesLock.leaseTime(), trionesLock.unit());
                if (locked) {
                    result = joinPoint.proceed();
                } else {
                    throw new RuntimeException("lock failed");
                }
            } finally {
                lock.unlock();
            }
        } else {
            result = joinPoint.proceed();
        }
        return result;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.beanResolver = new BeanFactoryResolver(applicationContext);
    }
}
