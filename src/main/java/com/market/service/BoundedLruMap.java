package com.market.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * A thread-safe map with a fixed maximum size that, when full, drops the least recently used entry. Used by the
 * in-memory limiters: a flood of new keys can't exhaust memory, and the limiter keeps working (new keys are always
 * tracked, and recently active ones survive) instead of refusing everyone or silently not counting.
 */
public final class BoundedLruMap<V> {

    private final Map<String, V> map;

    public BoundedLruMap(int maxEntries) {
        this.map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, V> eldest) {
                return size() > maxEntries;
            }
        };
    }

    public synchronized V get(String key) {
        return map.get(key);
    }

    public synchronized void put(String key, V value) {
        map.put(key, value);
    }

    public synchronized V remove(String key) {
        return map.remove(key);
    }

    public synchronized V compute(String key, BiFunction<String, V, V> remapping) {
        return map.compute(key, remapping);
    }

    public synchronized V computeIfPresent(String key, BiFunction<String, V, V> remapping) {
        return map.computeIfPresent(key, remapping);
    }

    public synchronized int size() {
        return map.size();
    }
}
