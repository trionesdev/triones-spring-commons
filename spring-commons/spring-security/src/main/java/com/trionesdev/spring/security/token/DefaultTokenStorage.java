package com.trionesdev.spring.security.token;

import java.util.HashMap;
import java.util.Map;

/**
 * 默认的Token存储实现
 *
 * @author bane.shi
 */
public class DefaultTokenStorage implements TokenStorage {

    public static final Map<String, Object> storeMap = new HashMap<>();

    @Override
    public Object get(String key) {
        return storeMap.get(key);
    }

    @Override
    public void set(String key, Object value, int expires) {
        storeMap.put(key, value);
    }

    @Override
    public void remove(String key) {
        storeMap.remove(key);
    }
}
