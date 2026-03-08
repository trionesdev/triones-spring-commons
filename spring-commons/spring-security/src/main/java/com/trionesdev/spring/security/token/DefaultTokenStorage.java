package com.trionesdev.spring.security.token;

import java.util.Map;

/**
 * 默认的Token存储实现
 * @author bane.shi
 */
public class DefaultTokenStorage implements TokenStorage {

    @Override
    public Map<String, Object> get(String key) {
        return Map.of();
    }

    @Override
    public void set(String key, String value) {

    }

    @Override
    public void remove(String key) {

    }
}
