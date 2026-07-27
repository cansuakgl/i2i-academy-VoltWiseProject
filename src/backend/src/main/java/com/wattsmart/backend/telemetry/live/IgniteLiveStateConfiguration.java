package com.wattsmart.backend.telemetry.live;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignition;
import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.client.ClientCache;
import org.apache.ignite.configuration.ClientConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class IgniteLiveStateConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "app.ignite.enabled", havingValue = "true")
    IgniteClient igniteClient(org.springframework.core.env.Environment environment) {
        String host = environment.getProperty("app.ignite.host", "localhost");
        int port = environment.getProperty("app.ignite.port", Integer.class, 10800);
        ClientConfiguration configuration = new ClientConfiguration()
                .setAddresses(host + ":" + port);
        log.info("Starting Ignite thin client. address={}:{}", host, port);
        return Ignition.startClient(configuration);
    }

    @Bean
    @ConditionalOnProperty(name = "app.live-state.store", havingValue = "ignite")
    ClientCache<UUID, LiveHomeState> liveHomeStateIgniteCache(
            IgniteClient igniteClient,
            org.springframework.core.env.Environment environment
    ) {
        String cacheName = environment.getProperty("app.ignite.caches.live-home-state", "live-home-state");
        log.info("Initializing Ignite live home state cache. cacheName={}", cacheName);
        return igniteClient.getOrCreateCache(cacheName);
    }

    @Bean
    @ConditionalOnProperty(name = "app.idempotency.store", havingValue = "ignite")
    ClientCache<String, String> idempotencyIgniteCache(
            IgniteClient igniteClient,
            org.springframework.core.env.Environment environment
    ) {
        String cacheName = environment.getProperty("app.ignite.caches.idempotency", "idempotency");
        java.time.Duration ttl = environment.getProperty(
                "app.idempotency.ttl",
                java.time.Duration.class,
                java.time.Duration.ofHours(24));
        log.info("Initializing Ignite idempotency cache. cacheName={}, ttl={}", cacheName, ttl);
        return igniteClient.<String, String>getOrCreateCache(cacheName)
                .withExpirePolicy(new CreatedExpiryPolicy(new Duration(TimeUnit.MILLISECONDS, ttl.toMillis())));
    }
}
