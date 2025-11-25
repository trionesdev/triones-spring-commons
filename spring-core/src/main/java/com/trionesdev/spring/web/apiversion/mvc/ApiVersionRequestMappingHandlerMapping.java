package com.trionesdev.spring.web.apiversion.mvc;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;



@Getter
public abstract class ApiVersionRequestMappingHandlerMapping extends RequestMappingHandlerMapping {
    private RequestMappingInfo info;

    @Override
    protected RequestMappingInfo getMatchingMapping(RequestMappingInfo info, HttpServletRequest request) {
        this.info = info;
        return info.getMatchingCondition(request);
    }
}
