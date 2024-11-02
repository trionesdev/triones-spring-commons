package com.trionesdev.spring.core.audit;

import com.trionesdev.spring.core.event.act.ActEventAspect;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;

import java.time.Instant;
import java.util.*;

@RequiredArgsConstructor
@Aspect
public class OperationAuditAspect extends ActEventAspect {

    private final List<OperationAuditProcess> processes;
    private Map<Class<?>, OperationAuditProcess> processMap;


    @PostConstruct
    public void init() {
        processMap = new HashMap<>();
        processes.forEach(process -> processMap.put(process.getClass(), process));
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
        OperationAuditProcess process = Objects.isNull(operationAudit) ? null : getProcess(operationAudit.process());
        if (operationAudit == null || process == null) {
            return joinPoint.proceed();
        }
        OperationAuditContext operationAuditContext = new OperationAuditContext();
        operationAuditContext.setStartAt(startAt);
        operationAuditContext.setType(operationAudit.type());
        operationAuditContext.setAction(operationAudit.action());
        operationAuditContext.setDescription(operationAudit.description());
        operationAuditContext.setArgs(mapArgs(joinPoint, methodSignature));
        operationAuditContext.setBeforeValue(process.before(operationAuditContext.getArgs()));
        Object result;
        try {
            result = joinPoint.proceed();
            operationAuditContext.setAfterValue(process.after(operationAuditContext.getArgs()));
            operationAuditContext.setSuccess(true);
        } catch (Throwable e) {
            operationAuditContext.setSuccess(false);
            operationAuditContext.setErrorMsg(e.getMessage());
            throw e;
        } finally {
            operationAuditContext.setEndAt(Instant.now());
            process.process(operationAuditContext);
        }
        return result;
    }

    public OperationAuditProcess getProcess(Class<?> clazz) {
        if (CollectionUtils.isEmpty(processes)) {
            return null;
        }
        if (clazz == Void.class) {
            if (processes.stream().filter(OperationAuditProcess::isDefault).count() > 1) {
                throw new RuntimeException("multi default process");
            }
            return processes.stream().filter(OperationAuditProcess::isDefault).findFirst().orElse(null);
        } else {
            OperationAuditProcess process = processMap.get(clazz);
            if (process == null) {
                throw new RuntimeException(clazz.getName() + " process not found");
            }
            return process;
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
