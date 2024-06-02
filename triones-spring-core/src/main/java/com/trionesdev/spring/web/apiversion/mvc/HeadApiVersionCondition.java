package com.trionesdev.spring.web.apiversion.mvc;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.condition.RequestCondition;

import java.util.Objects;

@Getter
public class HeadApiVersionCondition implements RequestCondition<HeadApiVersionCondition> {
    private final String headerVersionName;
    private final String headerVersion;

    public HeadApiVersionCondition(String headerVersionName, String headerVersion) {
        this.headerVersionName = headerVersionName;
        this.headerVersion = headerVersion;
    }


    @Override
    public HeadApiVersionCondition combine(HeadApiVersionCondition other) {
        return new HeadApiVersionCondition(other.getHeaderVersionName(), other.getHeaderVersion());
    }

    @Override
    public HeadApiVersionCondition getMatchingCondition(HttpServletRequest request) {
        String reqHeaderVersion = request.getHeader(headerVersionName);
        if (StringUtils.hasText(headerVersion) && Objects.equals(reqHeaderVersion, this.headerVersion)) {
            return this;
        }
        return null;
    }

    @Override
    public int compareTo(HeadApiVersionCondition other, HttpServletRequest request) {
        return 0;
    }

}
