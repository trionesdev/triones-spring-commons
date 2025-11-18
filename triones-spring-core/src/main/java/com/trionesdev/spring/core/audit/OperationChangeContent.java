package com.trionesdev.spring.core.audit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class OperationChangeContent {
    private List<OperationChangeItem> add;
    private List<OperationChangeItem> remove;
    private List<OperationChangeItem> update;

    public boolean isEmpty() {
        return CollectionUtils.isEmpty(add) && CollectionUtils.isEmpty(remove) && CollectionUtils.isEmpty(update);
    }

    public boolean isNotEmpty() {
        return CollectionUtils.isNotEmpty(add) || CollectionUtils.isNotEmpty(remove) || CollectionUtils.isNotEmpty(update);
    }
}
