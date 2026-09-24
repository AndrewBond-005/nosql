package ru.library.event.repository;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.Txn;
import io.etcd.jetcd.kv.TxnResponse;
import io.etcd.jetcd.op.Cmp;
import io.etcd.jetcd.op.CmpTarget;
import io.etcd.jetcd.op.Op;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Etcd-backed KeyValueRepository implementation.
 *
 * Key schema: all keys use "{prefix}{id}" format.
 *   event:{uuid}     -> Event JSON
 *   manager:{uuid}   -> Manager JSON
 *   order:{uuid}     -> Order JSON
 *   temp:{uuid}      -> TemporaryRequest JSON (with lease TTL)
 *   settings:{userId} -> UserSettings JSON
 *   views:{eventId}  -> counter value (plain long as string)
 *
 * Prefixes enable efficient range queries via Etcd prefix scan.
 * Counters store plain numeric strings for atomic increment via transactions.
 * Temporary requests use Etcd leases for automatic TTL-based expiration.
 */
public class EtcdKeyValueRepository<T> implements KeyValueRepository<T> {

    private static final Logger log = LoggerFactory.getLogger(EtcdKeyValueRepository.class);

    private final KV kvClient;
    private final Lease leaseClient;
    private final ObjectMapper objectMapper;
    private final Class<T> valueClass;

    public EtcdKeyValueRepository(KV kvClient, Lease leaseClient, Class<T> valueClass) {
        this.kvClient = kvClient;
        this.leaseClient = leaseClient;
        this.valueClass = valueClass;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void put(String key, T value) {
        try {
            byte[] valueBytes = objectMapper.writeValueAsBytes(value);
            kvClient.put(bs(key), bs(valueBytes)).get();
            log.debug("Etcd PUT key={}", key);
        } catch (JsonProcessingException e) {
            log.error("Serialization error for key={}", key, e);
            throw new RuntimeException("Serialization error for key: " + key, e);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Etcd put failed for key={}", key, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Etcd put failed for key: " + key, e);
        }
    }

    @Override
    public void putWithTtl(String key, T value, long ttlSeconds) {
        try {
            byte[] valueBytes = objectMapper.writeValueAsBytes(value);

            // Create a lease with the specified TTL.
            // Etcd automatically deletes all keys bound to this lease when it expires.
            long leaseId = leaseClient.grant(ttlSeconds).get().getID();

            // Put with lease attached — the key is now bound to the lease
            kvClient.put(bs(key), bs(valueBytes),
                    PutOption.newBuilder().withLeaseId(leaseId).build()).get();

            log.debug("Etcd PUT key={} with TTL={}s leaseId={}", key, ttlSeconds, leaseId);
        } catch (JsonProcessingException e) {
            log.error("Serialization error for key={}", key, e);
            throw new RuntimeException("Serialization error for key: " + key, e);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Etcd putWithTtl failed for key={}", key, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Etcd putWithTtl failed for key: " + key, e);
        }
    }

    @Override
    public Optional<T> get(String key) {
        try {
            List<KeyValue> kvs = kvClient.get(bs(key)).get().getKvs();
            if (kvs.isEmpty()) {
                log.debug("Etcd GET key={} -> not found", key);
                return Optional.empty();
            }
            KeyValue kv = kvs.get(0);
            T result = objectMapper.readValue(kv.getValue().getBytes(), valueClass);
            log.debug("Etcd GET key={} -> found", key);
            return Optional.of(result);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Etcd get failed for key={}", key, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Etcd get failed for key: " + key, e);
        } catch (IOException e) {
            log.error("Deserialization error for key={}", key, e);
            throw new RuntimeException("Deserialization error for key: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            kvClient.delete(bs(key)).get();
            log.debug("Etcd DELETE key={}", key);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Etcd delete failed for key={}", key, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Etcd delete failed for key: " + key, e);
        }
    }

    @Override
    public List<T> getAll(String prefix) {
        try {
            List<KeyValue> kvs = kvClient.get(
                    bs(prefix),
                    GetOption.newBuilder().isPrefix(true).build()
            ).get().getKvs();

            List<T> results = new ArrayList<>();
            for (KeyValue kv : kvs) {
                try {
                    T value = objectMapper.readValue(kv.getValue().getBytes(), valueClass);
                    results.add(value);
                } catch (IOException e) {
                    String k = new String(kv.getKey().getBytes(), StandardCharsets.UTF_8);
                    log.warn("Failed to deserialize key={}, skipping", k, e);
                }
            }
            log.debug("Etcd GETALL prefix={} -> {} results", prefix, results.size());
            return results;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Etcd getAll failed for prefix={}", prefix, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Etcd getAll failed for prefix: " + prefix, e);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            List<KeyValue> kvs = kvClient.get(bs(key)).get().getKvs();
            return !kvs.isEmpty();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Etcd exists check failed for key={}", key, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Etcd exists check failed for key: " + key, e);
        }
    }

    /**
     * Atomic counter increment using Etcd compare-and-swap transaction.
     *
     * Algorithm:
     * 1. Read current value and its modRevision (a monotonically increasing version number).
     * 2. Create a transaction: IF key's modRevision == our modRevision (no concurrent change),
     *    THEN put the new value.
     * 3. If the transaction fails (someone else modified the key), retry from step 1.
     *
     * This prevents lost updates in concurrent scenarios — exactly one writer succeeds
     * per revision, and others must re-read and retry.
     */
    @Override
    public long incrementCounter(String key, long delta) {
        int maxRetries = 10;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                List<KeyValue> kvs = kvClient.get(bs(key)).get().getKvs();

                long currentValue = 0;
                long modRevision = 0;

                if (!kvs.isEmpty()) {
                    KeyValue kv = kvs.get(0);
                    String val = new String(kv.getValue().getBytes(), StandardCharsets.UTF_8);
                    currentValue = Long.parseLong(val);
                    modRevision = kv.getModRevision();
                }

                long newValue = currentValue + delta;
                byte[] newValBytes = String.valueOf(newValue).getBytes(StandardCharsets.UTF_8);

                // Transaction: IF modRevision unchanged, THEN put new value
                Txn txn = kvClient.txn();
                txn.If(new Cmp(bs(key), Cmp.Op.EQUAL, CmpTarget.modRevision(modRevision)));
                txn.Then(Op.put(bs(key), bs(newValBytes), PutOption.DEFAULT));
                TxnResponse response = txn.commit().get();

                if (response.isSucceeded()) {
                    log.debug("Etcd INCREMENT key={} {} -> {} (attempt {})",
                            key, currentValue, newValue, attempt);
                    return newValue;
                }

                log.debug("Etcd INCREMENT key={} conflict on attempt {}/{}",
                        key, attempt, maxRetries);
            } catch (NumberFormatException e) {
                log.error("Counter value for key={} is not a number", key, e);
                throw new RuntimeException("Invalid counter value for key: " + key, e);
            } catch (InterruptedException | ExecutionException e) {
                log.error("Etcd incrementCounter failed for key={}", key, e);
                Thread.currentThread().interrupt();
                throw new RuntimeException("Etcd incrementCounter failed for key: " + key, e);
            }
        }
        throw new RuntimeException(
                "Etcd incrementCounter failed after " + maxRetries + " retries for key: " + key);
    }

    @Override
    public long getCounter(String key) {
        try {
            List<KeyValue> kvs = kvClient.get(bs(key)).get().getKvs();
            if (kvs.isEmpty()) {
                return 0;
            }
            String val = new String(kvs.get(0).getValue().getBytes(), StandardCharsets.UTF_8);
            return Long.parseLong(val);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Etcd getCounter failed for key={}", key, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Etcd getCounter failed for key: " + key, e);
        } catch (NumberFormatException e) {
            log.error("Counter value for key={} is not a number", key, e);
            return 0;
        }
    }

    /**
     * Creates a new lease with the given TTL and returns its ID.
     * Used by OrderService to manage temporary request lifetimes.
     */
    public long createLease(long ttlSeconds) {
        try {
            return leaseClient.grant(ttlSeconds).get().getID();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to create lease with TTL={}s", ttlSeconds, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to create lease", e);
        }
    }

    /**
     * Puts a value bound to an existing lease. The key will be automatically
     * deleted by Etcd when the lease expires.
     */
    public void putWithLeaseId(String key, T value, long leaseId) {
        try {
            byte[] valueBytes = objectMapper.writeValueAsBytes(value);
            kvClient.put(bs(key), bs(valueBytes),
                    PutOption.newBuilder().withLeaseId(leaseId).build()).get();
            log.debug("Etcd PUT key={} with leaseId={}", key, leaseId);
        } catch (JsonProcessingException e) {
            log.error("Serialization error for key={}", key, e);
            throw new RuntimeException("Serialization error for key: " + key, e);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Etcd putWithLeaseId failed for key={}", key, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Etcd putWithLeaseId failed for key: " + key, e);
        }
    }

    /**
     * Revokes (cancels) a lease, which also deletes all keys bound to it.
     */
    public void revokeLease(long leaseId) {
        try {
            leaseClient.revoke(leaseId).get();
            log.debug("Etcd lease revoked: leaseId={}", leaseId);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to revoke lease={}", leaseId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to revoke lease: " + leaseId, e);
        }
    }

    private static ByteSequence bs(String s) {
        return ByteSequence.from(s, StandardCharsets.UTF_8);
    }

    private static ByteSequence bs(byte[] bytes) {
        return ByteSequence.from(bytes);
    }
}
