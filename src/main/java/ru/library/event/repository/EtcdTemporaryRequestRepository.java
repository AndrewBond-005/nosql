package ru.library.event.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import ru.library.event.model.TemporaryRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Repository
public class EtcdTemporaryRequestRepository implements TemporaryRequestRepository {
    private static final Logger log = LoggerFactory.getLogger(EtcdTemporaryRequestRepository.class);
    private static final String PREFIX = "temp:";

    private final KV kvClient;
    private final Lease leaseClient;
    private final ObjectMapper objectMapper;

    public EtcdTemporaryRequestRepository(KV kvClient, Lease leaseClient, ObjectMapper objectMapper) {
        this.kvClient = kvClient;
        this.leaseClient = leaseClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void saveWithTtl(TemporaryRequest request, long ttlSeconds) {
        try {
            long leaseId = leaseClient.grant(ttlSeconds).get().getID();
            kvClient.put(
                    key(request.getId()),
                    ByteSequence.from(objectMapper.writeValueAsBytes(request)),
                    PutOption.newBuilder().withLeaseId(leaseId).build()
            ).get();
            log.info("Temporary request saved in Etcd: id={}, ttl={}s, leaseId={}",
                    request.getId(), ttlSeconds, leaseId);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize temporary request: " + request.getId(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Etcd operation was interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to save temporary request in Etcd", e);
        }
    }

    @Override
    public Optional<TemporaryRequest> findById(String id) {
        try {
            List<KeyValue> values = kvClient.get(key(id)).get().getKvs();
            if (values.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(values.get(0).getValue().getBytes(), TemporaryRequest.class));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deserialize temporary request: " + id, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Etcd operation was interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to read temporary request from Etcd", e);
        }
    }

    @Override
    public List<TemporaryRequest> findAll() {
        try {
            List<KeyValue> values = kvClient.get(
                    key(""),
                    GetOption.newBuilder().isPrefix(true).build()
            ).get().getKvs();
            List<TemporaryRequest> requests = new ArrayList<>();
            for (KeyValue value : values) {
                requests.add(objectMapper.readValue(value.getValue().getBytes(), TemporaryRequest.class));
            }
            return requests;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deserialize temporary requests", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Etcd operation was interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to read temporary requests from Etcd", e);
        }
    }

    @Override
    public void deleteById(String id) {
        try {
            kvClient.delete(key(id)).get();
            log.info("Temporary request deleted from Etcd: id={}", id);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Etcd operation was interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to delete temporary request from Etcd", e);
        }
    }

    private static ByteSequence key(String id) {
        return ByteSequence.from(PREFIX + id, StandardCharsets.UTF_8);
    }
}
