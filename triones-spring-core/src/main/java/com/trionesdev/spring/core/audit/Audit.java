package com.trionesdev.spring.core.audit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class Audit {
    private Object beforeValue;
    private Object afterValue;
    private String compareDescription;
    private String description;
}
