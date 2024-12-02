package com.trionesdev.spring.cache;

import java.util.concurrent.TimeUnit;

public interface CacheFacade<K, V> {
    void setValue(K key, V value, long timeout, TimeUnit unit);

    V getValue(K key);
}
