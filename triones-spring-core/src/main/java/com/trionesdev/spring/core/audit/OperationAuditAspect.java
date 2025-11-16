package com.trionesdev.spring.core.audit;

import com.trionesdev.spring.core.event.act.ActEventAspect;
import com.trionesdev.spring.core.event.act.ActEventEvaluationContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
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

import java.time.Instant;
import java.util.*;

@RequiredArgsConstructor
@Aspect
public class OperationAuditAspect extends ActEventAspect {

    private final List<OperationAuditHandler> handlers;
    private Map<Class<?>, OperationAuditHandler> handlerMap;


    @PostConstruct
    public void init() {
        handlerMap = new HashMap<>();
        handlers.forEach(process -> handlerMap.put(process.getClass(), process));
    }

    @Pointcut("@annotation(com.trionesdev.spring.core.audit.OperationAudit)")
    public void auditLogAround() {
    }

    @Around(value = "auditLogAround()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Instant startAt = Instant.now();
        Signature signature = joinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        OperationAudit operationAudit = AnnotationUtils.getAnnotation(methodSignature.getMethod(), OperationAudit.class);
        OperationAuditHandler handler = Objects.isNull(operationAudit) ? null : getHandler(operationAudit.handler());
        if (operationAudit == null || handler == null) {
            return joinPoint.proceed();
        }
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext evaluationContext = new ActEventEvaluationContext(joinPoint.getTarget(), methodSignature.getMethod(), joinPoint.getArgs(), new DefaultParameterNameDiscoverer());
        evaluationContext.setBeanResolver(this.beanResolver);
        OperationAuditContext operationAuditContext = new OperationAuditContext();
        if (StringUtils.isNoneBlank(operationAudit.subject())) {
            Object subject = parser.parseExpression(operationAudit.subject()).getValue(evaluationContext);
            if (subject != null) {
                operationAuditContext.setSubject(subject.toString());
            }
        }

        operationAuditContext.setStartAt(startAt);
        operationAuditContext.setType(operationAudit.type());
        operationAuditContext.setDomain(operationAudit.domain());
        operationAuditContext.setCategory(operationAudit.category());
        operationAuditContext.setAction(operationAudit.action());
        operationAuditContext.setDescription(operationAudit.description());
        operationAuditContext.setRequest(mapArgs(joinPoint, methodSignature));
        operationAuditContext.setBeforeContent(handler.beforeContent(operationAuditContext, operationAuditContext.getRequest()));
        Object result;
        try {
            result = joinPoint.proceed();
            operationAuditContext.setResponse(result);
            operationAuditContext.setAfterContent(handler.afterContent(operationAuditContext, operationAuditContext.getRequest()));
            operationAuditContext.setSuccess(true);
        } catch (Throwable e) {
            operationAuditContext.setSuccess(false);
            operationAuditContext.setErrorMsg(e.getMessage());
            throw e;
        } finally {
            operationAuditContext.setEndAt(Instant.now());
            handler.handle(operationAuditContext);
        }
        return result;
    }

    public OperationAuditHandler getHandler(Class<?> clazz) {
        if (CollectionUtils.isEmpty(handlers)) {
            return null;
        }
        if (clazz == Void.class) {
            if (handlers.stream().filter(OperationAuditHandler::isDefault).count() > 1) {
                throw new RuntimeException("multi default handlers");
            }
            return handlers.stream().filter(OperationAuditHandler::isDefault).findFirst().orElse(null);
        } else {
            OperationAuditHandler handler = handlerMap.get(clazz);
            if (handler == null) {
                throw new RuntimeException(clazz.getName() + " handler not found");
            }
            return handler;
        }
    }

    public Map<String, Object> mapArgs(ProceedingJoinPoint joinPoint, MethodSignature methodSignature) {
        Object[] args = joinPoint.getArgs();
        String[] parameterNames = methodSignature.getParameterNames();
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < parameterNames.length; i++) {
            if (args.length > i) {
                map.put(parameterNames[i], args[i]);
            }
        }
        return map;
    }

}
