package com.trionesdev.spring.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Optional;

public abstract class AbstractCacheFacade<K, V> implements CacheFacade<K, V> {
    private final CacheManager cacheManager;

    protected AbstractCacheFacade(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }


    @Override
    public void put(String cacheName, K key, V value) {
        Optional.ofNullable(cacheManager.getCache(cacheName)).ifPresent(cache -> cache.put(key, value));
    }


    @Override
    public V get(String cacheName, K key, Class<V> vClass) {
        return Optional.ofNullable(cacheManager.getCache(cacheName)).map(cache -> cache.get(key, vClass)).orElse(null);
    }

    @Override
    public void clean(String cacheName) {
        Optional.ofNullable(cacheManager.getCache(cacheName)).ifPresent(Cache::clear);
    }

    @Override
    public void evict(String cacheName, K key) {
        Optional.ofNullable(cacheManager.getCache(cacheName)).ifPresent(cache -> cache.evict(key));
    }
}
