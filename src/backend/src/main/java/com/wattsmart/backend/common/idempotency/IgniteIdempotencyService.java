package com.wattsmart.backend.common.idempotency;

import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.client.ClientCache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.idempotency.store", havingValue = "ignite")
@Slf4j
public class IgniteIdempotencyService implements IdempotencyService {

    private final ClientCache<String, String> idempotencyCache;

    public IgniteIdempotencyService(ClientCache<String, String> idempotencyIgniteCache) {
        this.idempotencyCache = idempotencyIgniteCache;
    }

    @Override
    public boolean tryClaim(String key) {
        try {
            return idempotencyCache.putIfAbsent(key, OffsetDateTime.now().toString());
        } catch (RuntimeException exception) {
            log.error("Ignite idempotency claim failed. key={}", key, exception);
            throw exception;
        }
    }
}
