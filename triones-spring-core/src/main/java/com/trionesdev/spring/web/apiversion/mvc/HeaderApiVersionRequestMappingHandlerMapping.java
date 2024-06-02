package com.trionesdev.spring.web.apiversion.mvc;

import com.trionesdev.spring.web.apiversion.HeaderApiVersion;
import lombok.AllArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.condition.RequestCondition;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;


public class HeaderApiVersionRequestMappingHandlerMapping extends ApiVersionRequestMappingHandlerMapping {
    private final String headerVersionName;

    public HeaderApiVersionRequestMappingHandlerMapping() {
        this.headerVersionName = "X-Api-Version";
    }

    public HeaderApiVersionRequestMappingHandlerMapping(String headerVersionName) {
        this.headerVersionName = headerVersionName;
    }

    @Override
    protected RequestCondition<?> getCustomTypeCondition(Class<?> handlerType) {
        return createVersionCondition(handlerType.getAnnotation(HeaderApiVersion.class));
    }

    @Override
    protected RequestCondition<?> getCustomMethodCondition(Method method) {
        return createVersionCondition(method.getAnnotation(HeaderApiVersion.class));
    }

    private RequestCondition<HeadApiVersionCondition> createVersionCondition(HeaderApiVersion headerVersion) {
        if (Objects.isNull(headerVersion) || !StringUtils.hasText(headerVersion.value())) {
            return null;
        }
        return new HeadApiVersionCondition(headerVersionName,
                Optional.ofNullable(headerVersion).map(HeaderApiVersion::value).orElse(null)
        );
    }


}
