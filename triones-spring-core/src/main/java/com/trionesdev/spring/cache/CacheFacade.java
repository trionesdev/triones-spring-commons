package com.trionesdev.spring.cache;

import java.util.concurrent.TimeUnit;

public interface CacheFacade<K, V> {
    void put(K key, V value, long timeout, TimeUnit unit);

    void put(K key, V value);

    V get(K key);

    void evict(K key);

    void put(String cacheName, K key, V value);

    V get(String cacheName, K key, Class<V> vClass);

    void clean(String cacheName);

    void evict(String cacheName, K key);
}
