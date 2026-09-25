package ru.library.event.config;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.Lease;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

@Configuration
public class RepositoryConfig {

    @Bean(destroyMethod = "close")
    public Client etcdClient(@Value("${etcd.endpoints}") String endpoints,
                             @Value("${etcd.namespace}") String namespace) {
        return Client.builder()
                .endpoints(endpoints)
                .namespace(ByteSequence.from(namespace, StandardCharsets.UTF_8))
                .build();
    }

    @Bean
    public KV etcdKV(Client etcdClient) {
        return etcdClient.getKVClient();
    }

    @Bean
    public Lease etcdLease(Client etcdClient) {
        return etcdClient.getLeaseClient();
    }
}
