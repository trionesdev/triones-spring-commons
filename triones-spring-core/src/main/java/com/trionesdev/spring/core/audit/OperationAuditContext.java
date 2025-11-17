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
    private boolean batch;
    private Map<String, Object> request;
    private Object response;
    private String beforeContent;
    private String afterContent;
    private String type;
    private String category;
    private String domain;
    private String action;
    private String subject;
    private String subjectId;
    private String description;
    private String descriptionResourceKey;
    private Instant startAt;
    private Instant endAt;
    private Boolean success;
    private String errorMsg;
}
