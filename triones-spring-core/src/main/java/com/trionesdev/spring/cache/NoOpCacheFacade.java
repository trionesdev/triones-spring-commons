package com.trionesdev.spring.cache;

import org.springframework.cache.CacheManager;

import java.util.concurrent.TimeUnit;

public class NoOpCacheFacade <K, V> extends AbstractCacheFacade<K, V>{
    public NoOpCacheFacade(CacheManager cacheManager) {
        super(cacheManager);
    }

    @Override
    public void put(K key, V value, long timeout, TimeUnit unit) {

    }

    @Override
    public void put(K key, V value) {

    }

    @Override
    public V get(K key) {
        return null;
    }

    @Override
    public void evict(K key) {

    }
}
