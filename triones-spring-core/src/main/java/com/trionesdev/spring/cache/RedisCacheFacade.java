package com.trionesdev.spring.cache;

import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.TimeUnit;

public class RedisCacheFacade<K, V> extends AbstractCacheFacade<K, V> {
    private final RedisTemplate<K, V> redisTemplate;

    public RedisCacheFacade(CacheManager cacheManager, RedisTemplate<K, V> redisTemplate) {
        super(cacheManager);
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void put(K key, V value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    @Override
    public void put(K key, V value) {
        redisTemplate.opsForValue().set(key, value);
    }


    @Override
    public V get(K key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void evict(K key) {
        redisTemplate.delete(key);
    }

}
