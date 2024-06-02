package com.trionesdev.spring.web.apiversion.mvc;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import org.springframework.http.server.PathContainer;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.condition.RequestCondition;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;

import java.util.Map;
import java.util.Objects;

@Getter
public class PathApiVersionCondition implements RequestCondition<PathApiVersionCondition> {
    private final ApiVersionRequestMappingHandlerMapping mapping;
    private final String pathVersionName;
    private final String pathVersion;

    public PathApiVersionCondition(ApiVersionRequestMappingHandlerMapping mapping, String pathVersionName, String pathVersion) {
        this.mapping = mapping;
        this.pathVersionName = pathVersionName;
        this.pathVersion = pathVersion;
    }


    @Override
    public PathApiVersionCondition combine(PathApiVersionCondition other) {
        return new PathApiVersionCondition(other.getMapping(), other.getPathVersionName(), other.getPathVersion());
    }

    @Override
    public PathApiVersionCondition getMatchingCondition(HttpServletRequest request) {
        PathContainer path = ServletRequestPathUtils.getParsedRequestPath(request).pathWithinApplication();
        assert this.getMapping().getInfo().getPathPatternsCondition() != null;
        PathPattern bestPattern = this.getMapping().getInfo().getPathPatternsCondition().getFirstPattern();
        PathPattern.PathMatchInfo result = bestPattern.matchAndExtract(path);
        Assert.notNull(result, () ->
                "Expected bestPattern: " + bestPattern + " to match lookupPath " + path);
        Map<String, String> uriVariables = result.getUriVariables();
        if (StringUtils.hasText(pathVersion) && Objects.equals(uriVariables.get(pathVersionName), this.pathVersion)) {
            return this;
        }
        return null;
    }

    @Override
    public int compareTo(PathApiVersionCondition other, HttpServletRequest request) {
        return 0;
    }

}
