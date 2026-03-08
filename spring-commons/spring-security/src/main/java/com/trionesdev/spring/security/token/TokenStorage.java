package com.trionesdev.spring.security.token;

import java.util.Map;

public interface TokenStorage {
    Map<String, Object> get(String key);

    void set(String key, String value);

    void remove(String key);
}
