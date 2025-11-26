package com.trionesdev.spring.core.audit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class OperationChangeItem {
    private boolean array;
    private boolean object;
    /**
     * 属性
     */
    private String property;
    /**
     * 属性名称
     */
    private String name;
    /**
     * 属性名称资源key
     */
    private String nameResourceKey;
    /**
     * 执行前的属性值
     */
    private Object beforeValue;
    /**
     * 执行后的属性值
     */
    private Object afterValue;
}
