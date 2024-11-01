package com.trionesdev.spring.core.audit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class OperationAuditContext {
    private Boolean success;
    private Map<String, Object> args;
    private Map<String, Object> beforeValue;
    private Map<String, Object> afterValue;
    private String type;
    private String description;
}
