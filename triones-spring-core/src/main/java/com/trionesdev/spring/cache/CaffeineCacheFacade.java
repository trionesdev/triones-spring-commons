package com.trionesdev.spring.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.CaffeineSpec;
import org.springframework.cache.CacheManager;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class CaffeineCacheFacade<K, V> extends AbstractCacheFacade<K, V> {
    private Caffeine<Object, Object> cacheBuilder = Caffeine.newBuilder();
    private Cache<K, V> cache;

    public CaffeineCacheFacade(CacheManager cacheManager) {
        super(cacheManager);
    }


    public void setCacheSpecification(String cacheSpecification) {
        doSetCaffeine(Caffeine.from(cacheSpecification));
    }

    public void setCaffeineSpec(CaffeineSpec caffeineSpec) {
        doSetCaffeine(Caffeine.from(caffeineSpec));
    }

    public void setCaffeine(Caffeine<Object, Object> caffeine) {
        Assert.notNull(caffeine, "Caffeine must not be null");
        doSetCaffeine(caffeine);
    }

    private void doSetCaffeine(Caffeine<Object, Object> cacheBuilder) {
        if (!ObjectUtils.nullSafeEquals(this.cacheBuilder, cacheBuilder)) {
            this.cacheBuilder = cacheBuilder;
        }
    }

    @Override
    public void put(K key, V value, long timeout, TimeUnit unit) {
        getCache().policy().expireVariably().ifPresentOrElse(expireVariably -> {
            expireVariably.put(key, value, timeout, unit);
        }, () -> getCache().put(key, value));
    }

    @Override
    public void put(K key, V value) {
        getCache().put(key, value);
    }

    @Override
    public V get(K key) {
        return getCache().getIfPresent(key);
    }

    @Override
    public void evict(K key) {
        getCache().asMap().remove(key);
    }


    public Cache<K, V> getCache() {
        if (Objects.isNull(cache)) {
            cache = cacheBuilder.build();
        }
        return cache;
    }

}
