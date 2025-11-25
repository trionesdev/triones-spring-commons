package com.trionesdev.spring.cache;

import org.springframework.cache.CacheManager;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class SimpleCacheFacade<K, V> extends AbstractCacheFacade<K, V> {
    private final ConcurrentMap<K, V> cache = new ConcurrentHashMap<>();

    public SimpleCacheFacade(CacheManager cacheManager) {
        super(cacheManager);
    }

    @Override
    public void set(K key, V value, Duration timeout) {
        cache.put(key, value);
    }

    @Override
    public void set(K key, V value, long timeout, TimeUnit unit) {
        cache.put(key, value);
    }

    @Override
    public void set(K key, V value) {
        cache.put(key, value);
    }

    @Override
    public V get(K key) {
        return cache.get(key);
    }

    @Override
    public void evict(K key) {
        cache.remove(key);
    }
}
