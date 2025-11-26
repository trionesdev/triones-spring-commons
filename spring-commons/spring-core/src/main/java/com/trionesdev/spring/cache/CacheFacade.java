package com.trionesdev.spring.cache;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public interface CacheFacade<K, V> {
    void set(K key, V value, Duration timeout);

    void set(K key, V value, long timeout, TimeUnit unit);

    void set(K key, V value);

    V get(K key);

    void evict(K key);

    void set(String cacheName, K key, V value);

    V get(String cacheName, K key, Class<V> vClass);

    void clean(String cacheName);

    void evict(String cacheName, K key);
}
