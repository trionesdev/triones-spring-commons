package com.trionesdev.spring.core.audit;

import java.util.Map;

public abstract class OperationAuditHandler {

    public Boolean isDefault() {
        return false;
    }

    public Map<String, Object> beforeContent(Map<String, Object> args) {
        return null;
    }

    public Map<String, Object> afterContent(Map<String, Object> args) {
        return null;
    }

    public abstract void handle(OperationAuditContext operationAuditContext);

}
