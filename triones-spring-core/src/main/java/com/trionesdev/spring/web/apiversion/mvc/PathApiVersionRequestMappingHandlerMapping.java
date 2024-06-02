package com.trionesdev.spring.web.apiversion.mvc;

import com.trionesdev.spring.web.apiversion.PathApiVersion;
import lombok.Getter;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.condition.RequestCondition;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;

@Getter
public class PathApiVersionRequestMappingHandlerMapping extends ApiVersionRequestMappingHandlerMapping {

    private final String pathVersionName;

    public PathApiVersionRequestMappingHandlerMapping() {
        this.pathVersionName = "version";
    }

    public PathApiVersionRequestMappingHandlerMapping(String pathVersionName) {
        this.pathVersionName = pathVersionName;
    }

    @Override
    protected RequestCondition<?> getCustomTypeCondition(Class<?> handlerType) {
        return createVersionCondition(handlerType.getAnnotation(PathApiVersion.class));
    }

    @Override
    protected RequestCondition<?> getCustomMethodCondition(Method method) {
        return createVersionCondition(method.getAnnotation(PathApiVersion.class));
    }

    private RequestCondition<PathApiVersionCondition> createVersionCondition(PathApiVersion pathVersion) {
        if (Objects.isNull(pathVersion) || !StringUtils.hasText(pathVersion.value())) {
            return null;
        }
        return new PathApiVersionCondition(this, pathVersionName, Optional.ofNullable(pathVersion).map(PathApiVersion::value).orElse(null));
    }

}
