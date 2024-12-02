package com.trionesdev.spring.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class RedisCacheFacade<K, V> implements CacheFacade<K, V> {

    private final RedisTemplate<K, V> redisTemplate;

    @Override
    public void setValue(K key, V value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    @Override
    public void setValue(K key, V value) {
        redisTemplate.opsForValue().set(key, value);
    }

    @Override
    public V getValue(K key) {
        return redisTemplate.opsForValue().get(key);
    }
}
