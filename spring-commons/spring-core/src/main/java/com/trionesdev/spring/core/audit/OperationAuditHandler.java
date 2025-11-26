package com.trionesdev.spring.core.audit;

import java.util.Map;

public abstract class OperationAuditHandler {

    public Boolean isDefault() {
        return false;
    }

    public String beforeContent(OperationAuditContext context,Map<String, Object> args) {
        return null;
    }

    public String afterContent(OperationAuditContext context,Map<String, Object> args) {
        return null;
    }

    public abstract void handle(OperationAuditContext context);

}
