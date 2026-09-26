package ru.library.event.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import java.util.Optional;

public class LoggingCache extends ConcurrentMapCache {
    private static final Logger log = LoggerFactory.getLogger(LoggingCache.class);

    private final boolean loggingEnabled;

    public LoggingCache(String name, boolean loggingEnabled) {
        super(name);
        this.loggingEnabled = loggingEnabled;
    }

    @Override
    public Cache.ValueWrapper get(Object key) {
        Cache.ValueWrapper wrapper = super.get(key);
        if (wrapper == null) {
            trace("MISS  ", key, "value will be loaded from PostgreSQL");
        } else {
            trace("HIT   ", key, "value=" + describe(wrapper.get()));
        }
        return wrapper;
    }

    @Override
    public void put(Object key, Object value) {
        super.put(key, value);
        trace("PUT   ", key, "value=" + describe(value));
    }

    @Override
    public Cache.ValueWrapper putIfAbsent(Object key, Object value) {
        Cache.ValueWrapper existing = super.putIfAbsent(key, value);
        if (existing == null) {
            trace("PUT   ", key, "value=" + describe(value));
        } else {
            trace("HIT   ", key, "value=" + describe(existing.get()));
        }
        return existing;
    }

    @Override
    public void evict(Object key) {
        super.evict(key);
        trace("EVICT ", key, "key removed from cache");
    }

    @Override
    public boolean evictIfPresent(Object key) {
        boolean evicted = super.evictIfPresent(key);
        if (evicted) {
            trace("EVICT ", key, "key removed from cache");
        } else {
            trace("MISS  ", key, "nothing to evict, PostgreSQL was not touched");
        }
        return evicted;
    }

    @Override
    public void clear() {
        super.clear();
        if (loggingEnabled) {
            log.info("CACHE CLEAR cache={} all keys removed", getName());
        }
    }

    private void trace(String operation, Object key, String details) {
        if (loggingEnabled) {
            log.info("CACHE {} cache={} key={} {}", operation, getName(), key, details);
        }
    }

    private String describe(Object value) {
        Object actual = value instanceof Optional<?> optional ? optional.orElse(null) : value;
        if (actual == null) {
            return "empty";
        }
        if (actual instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        return actual.getClass().getSimpleName();
    }
}
