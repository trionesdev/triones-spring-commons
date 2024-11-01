package com.trionesdev.spring.core.audit;

import com.trionesdev.spring.core.event.act.ActEventAspect;
import com.trionesdev.spring.core.event.act.ActEventEvaluationContext;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

@RequiredArgsConstructor
@Aspect
public class AuditLogAspect extends ActEventAspect {

    @Autowired
    private AuditLogProcess auditLogProcess;

    @Pointcut("@annotation(AuditLog)")
    public void auditLogAround() {
    }

    @Around(value = "auditLogAround()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Signature signature = joinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        AuditLog auditLog = AnnotationUtils.getAnnotation(methodSignature.getMethod(), AuditLog.class);
        if (auditLog == null) {
            return joinPoint.proceed();
        }
        Object result = null;
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context  = new ActEventEvaluationContext(joinPoint.getTarget(), methodSignature.getMethod(), joinPoint.getArgs(), new DefaultParameterNameDiscoverer());
        context.setBeanResolver(this.beanResolver);
        if (StringUtils.isNoneBlank(auditLog.before())) {
            Object beforeResult = parser.parseExpression(auditLog.before()).getValue(context);
        }
        result = joinPoint.proceed();
        if (StringUtils.isNoneBlank(auditLog.after())){
            Object afterResult = parser.parseExpression(auditLog.after()).getValue(context);
        }

        return result;
    }

}
