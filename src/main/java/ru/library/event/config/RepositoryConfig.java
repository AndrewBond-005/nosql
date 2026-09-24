package ru.library.event.config;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.Lease;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.library.event.model.Event;
import ru.library.event.model.Manager;
import ru.library.event.model.Order;
import ru.library.event.model.TemporaryRequest;
import ru.library.event.model.UserSettings;
import ru.library.event.repository.EtcdKeyValueRepository;

@Configuration
@Profile("etcd")
public class RepositoryConfig {

    private static final Logger log = LoggerFactory.getLogger(RepositoryConfig.class);

    @Value("${etcd.endpoints:http://localhost:2379}")
    private String endpoints;

    @Value("${etcd.namespace:library}")
    private String namespace;

    @Bean(destroyMethod = "close")
    public Client etcdClient() {
        log.info("Connecting to Etcd at {} with namespace '{}'", endpoints, namespace);
        Client client = Client.builder()
                .endpoints(endpoints)
                .namespace(ByteSequence.from(namespace, java.nio.charset.StandardCharsets.UTF_8))
                .build();
        log.info("Etcd client connected successfully");
        return client;
    }

    @Bean
    public KV etcdKV(Client etcdClient) {
        return etcdClient.getKVClient();
    }

    @Bean
    public Lease etcdLease(Client etcdClient) {
        return etcdClient.getLeaseClient();
    }

    /**
     * Typed repository beans — each bound to its concrete model class.
     * This avoids generic type erasure issues with Spring DI:
     * each service gets its own repository instance with the correct Class<T>.
     */

    @Bean
    public EtcdKeyValueRepository<Event> eventRepository(KV etcdKV, Lease etcdLease) {
        return new EtcdKeyValueRepository<>(etcdKV, etcdLease, Event.class);
    }

    @Bean
    public EtcdKeyValueRepository<Manager> managerRepository(KV etcdKV, Lease etcdLease) {
        return new EtcdKeyValueRepository<>(etcdKV, etcdLease, Manager.class);
    }

    @Bean
    public EtcdKeyValueRepository<Order> orderRepository(KV etcdKV, Lease etcdLease) {
        return new EtcdKeyValueRepository<>(etcdKV, etcdLease, Order.class);
    }

    @Bean
    public EtcdKeyValueRepository<TemporaryRequest> tempRequestRepository(KV etcdKV, Lease etcdLease) {
        return new EtcdKeyValueRepository<>(etcdKV, etcdLease, TemporaryRequest.class);
    }

    @Bean
    public EtcdKeyValueRepository<UserSettings> settingsRepository(KV etcdKV, Lease etcdLease) {
        return new EtcdKeyValueRepository<>(etcdKV, etcdLease, UserSettings.class);
    }
}
