package ru.library.event.repository;

import java.util.List;
import java.util.Optional;

public interface KeyValueRepository<T> {
    void put(String key, T value);
    void putWithTtl(String key, T value, long ttlSeconds);
    Optional<T> get(String key);
    void delete(String key);
    List<T> getAll(String prefix);
    boolean exists(String key);
    long incrementCounter(String key, long delta);
    long getCounter(String key);
}
