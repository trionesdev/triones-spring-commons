package com.trionesdev.spring.security.token;

public interface TokenStorage {
    Object get(String key);

    void set(String key, Object value,int expires);

    void remove(String key);
}
