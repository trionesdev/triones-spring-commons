package com.trionesdev.spring.core.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.trionesdev.commons.core.util.JacksonUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OperationAuditUtils {

    private static JsonNode convertToJsonNode(String content) {
        if (StringUtils.isBlank(content)) {
            return null;
        }
        try {
            return JacksonUtils.getObjectMapper().readTree(content);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static JsonNode getJsonNode(JsonNode content, String property) {
        String[] properties = property.split("\\.");
        JsonNode jsonNode = content;
        for (String propertyItem : properties) {
            if (jsonNode != null) {
                if (!jsonNode.has(propertyItem)) {
                    return null;
                }
                if (jsonNode.isArray()) {
                    jsonNode = jsonNode.get(Integer.parseInt(propertyItem));
                } else {
                    jsonNode = jsonNode.get(propertyItem);
                }
            }
        }
        return jsonNode;
    }

    private static boolean isEmpty(JsonNode jsonNode) {
        return jsonNode == null || jsonNode.isNull() || (jsonNode.isTextual() && StringUtils.isBlank(jsonNode.asText()));
    }

    public static OperationChangeContent changeContent(String before, String after, List<CompareProperty> compareProperties) {
        JsonNode beforeContent = convertToJsonNode(before);
        JsonNode afterContent = convertToJsonNode(after);
        List<OperationChangeItem> addItems = new ArrayList<>();
        List<OperationChangeItem> removeItems = new ArrayList<>();
        List<OperationChangeItem> updateItems = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(compareProperties)) {
            for (CompareProperty compareProperty : compareProperties) {
                JsonNode beforeJsonNode = getJsonNode(beforeContent, compareProperty.getProperty());
                JsonNode afterJsonNode = getJsonNode(afterContent, compareProperty.getProperty());
                if (isEmpty(beforeJsonNode) && isEmpty(afterJsonNode)) {
                    continue;
                }
                if (Objects.equals(beforeJsonNode, afterJsonNode)) {
                    continue;
                }
                boolean isArray = (Objects.nonNull(beforeJsonNode) && !beforeJsonNode.isNull() && beforeJsonNode.isArray()) || (Objects.nonNull(afterJsonNode) && !afterJsonNode.isNull() && afterJsonNode.isArray());
                boolean isObject = (Objects.nonNull(beforeJsonNode) && !beforeJsonNode.isNull() && beforeJsonNode.isObject()) || (Objects.nonNull(afterJsonNode) && !afterJsonNode.isNull() && afterJsonNode.isObject());
                if (isEmpty(beforeJsonNode)) {
                    addItems.add(OperationChangeItem.builder()
                            .property(compareProperty.getProperty())
                            .name(compareProperty.getName())
                            .nameResourceKey(compareProperty.getNameResourceKey())
                            .afterValue(afterJsonNode)
                            .array(isArray)
                            .object(isObject)
                            .build());
                } else if (isEmpty(afterJsonNode)) {
                    removeItems.add(OperationChangeItem.builder()
                            .property(compareProperty.getProperty())
                            .name(compareProperty.getName())
                            .nameResourceKey(compareProperty.getNameResourceKey())
                            .beforeValue(beforeJsonNode)
                            .array(isArray)
                            .object(isObject)
                            .build());
                } else {
                    updateItems.add(OperationChangeItem.builder()
                            .property(compareProperty.getProperty())
                            .name(compareProperty.getName())
                            .nameResourceKey(compareProperty.getNameResourceKey())
                            .beforeValue(beforeJsonNode)
                            .afterValue(afterJsonNode)
                            .array(isArray)
                            .object(isObject)
                            .build());
                }
            }
        }
        return OperationChangeContent.builder().add(addItems).remove(removeItems).update(updateItems).build();
    }

}
