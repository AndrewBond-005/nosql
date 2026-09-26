package ru.library.event.repository;

import ru.library.event.model.TemporaryRequest;

import java.util.List;
import java.util.Optional;

public interface TemporaryRequestRepository {
    void saveWithTtl(TemporaryRequest request, long ttlSeconds);
    Optional<TemporaryRequest> findById(String id);
    List<TemporaryRequest> findAll();
    void deleteById(String id);
}
