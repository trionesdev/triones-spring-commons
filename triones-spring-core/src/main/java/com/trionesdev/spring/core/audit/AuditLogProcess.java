package com.trionesdev.spring.core.audit;

import java.util.Map;

public abstract class AuditLogProcess {

    public Map<String, Object> before() {
        return null;
    }

    public Map<String, Object> after() {
        return null;
    }


    public String compare(Object oldValue, Object newValue) {
        return null;
    }

    public void process(Audit audit) {
    }
}
