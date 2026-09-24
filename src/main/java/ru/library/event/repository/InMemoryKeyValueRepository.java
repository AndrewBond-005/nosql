package ru.library.event.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
@Profile("inmemory")
public class InMemoryKeyValueRepository<T> implements KeyValueRepository<T> {

    private static class Entry<T> {
        T value;
        LocalDateTime expiresAt;

        Entry(T value, LocalDateTime expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
        }
    }

    private final ConcurrentHashMap<String, Entry<T>> store = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    @Override
    public void put(String key, T value) {
        store.put(key, new Entry<>(value, null));
    }

    @Override
    public void putWithTtl(String key, T value, long ttlSeconds) {
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(ttlSeconds);
        store.put(key, new Entry<>(value, expiresAt));
    }

    @Override
    public Optional<T> get(String key) {
        Entry<T> entry = store.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpired()) {
            store.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value);
    }

    @Override
    public void delete(String key) {
        store.remove(key);
        counters.remove(key);
    }

    @Override
    public List<T> getAll(String prefix) {
        return store.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .filter(e -> !e.getValue().isExpired())
                .map(e -> e.getValue().value)
                .collect(Collectors.toList());
    }

    @Override
    public boolean exists(String key) {
        Entry<T> entry = store.get(key);
        if (entry == null) {
            return false;
        }
        if (entry.isExpired()) {
            store.remove(key);
            return false;
        }
        return true;
    }

    @Override
    public long incrementCounter(String key, long delta) {
        return counters.computeIfAbsent(key, k -> new AtomicLong(0))
                .addAndGet(delta);
    }

    @Override
    public long getCounter(String key) {
        AtomicLong counter = counters.get(key);
        return counter != null ? counter.get() : 0;
    }
}
