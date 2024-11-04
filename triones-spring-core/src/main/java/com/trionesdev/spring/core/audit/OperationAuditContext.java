package com.trionesdev.spring.core.audit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.Map;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class OperationAuditContext {
    private Map<String, Object> request;
    private Object response;
    private Map<String, Object> beforeValues;
    private Map<String, Object> afterValues;
    private String type;
    private String category;
    private String action;
    private String description;
    private Instant startAt;
    private Instant endAt;
    private Boolean success;
    private String errorMsg;
}
