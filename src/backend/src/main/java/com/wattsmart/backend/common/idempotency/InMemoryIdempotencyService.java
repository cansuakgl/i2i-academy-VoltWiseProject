package com.wattsmart.backend.common.idempotency;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(IdempotencyService.class)
public class InMemoryIdempotencyService implements IdempotencyService {

    private final Map<String, Instant> claimedKeys = new ConcurrentHashMap<>();
    private final Duration ttl;

    public InMemoryIdempotencyService(@Value("${app.idempotency.ttl:PT24H}") Duration ttl) {
        this.ttl = ttl;
    }

    @Override
    public boolean tryClaim(String key) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        purgeExpiredKeys(now);
        return claimedKeys.compute(key, (ignored, existingExpiresAt) -> {
            if (existingExpiresAt == null || !existingExpiresAt.isAfter(now)) {
                return expiresAt;
            }
            return existingExpiresAt;
        }).equals(expiresAt);
    }

    private void purgeExpiredKeys(Instant now) {
        claimedKeys.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    }
}
