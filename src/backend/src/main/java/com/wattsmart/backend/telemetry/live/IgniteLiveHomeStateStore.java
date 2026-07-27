package com.wattsmart.backend.telemetry.live;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.client.ClientCache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.live-state.store", havingValue = "ignite")
@Slf4j
public class IgniteLiveHomeStateStore implements LiveHomeStateStore {

    private final ClientCache<UUID, LiveHomeState> liveHomeStateCache;

    public IgniteLiveHomeStateStore(ClientCache<UUID, LiveHomeState> liveHomeStateCache) {
        this.liveHomeStateCache = liveHomeStateCache;
    }

    @Override
    public LiveHomeState getHomeState(UUID homeId) {
        try {
            return liveHomeStateCache.get(homeId);
        } catch (RuntimeException exception) {
            log.error("Ignite live state read failed. homeId={}", homeId, exception);
            throw exception;
        }
    }

    @Override
    public Map<UUID, LiveHomeState> getHomeStates(Collection<UUID> homeIds) {
        if (homeIds.isEmpty()) {
            return Map.of();
        }
        try {
            return liveHomeStateCache.getAll(Set.copyOf(homeIds));
        } catch (RuntimeException exception) {
            log.error("Ignite live state batch read failed. homeCount={}", homeIds.size(), exception);
            throw exception;
        }
    }

    @Override
    public Map<UUID, LiveHomeState> getAllHomeStates() {
        Map<UUID, LiveHomeState> states = new HashMap<>();
        try (QueryCursor<Cache.Entry<UUID, LiveHomeState>> cursor = liveHomeStateCache.query(new ScanQuery<>())) {
            for (Cache.Entry<UUID, LiveHomeState> entry : cursor) {
                states.put(entry.getKey(), entry.getValue());
            }
        } catch (RuntimeException exception) {
            log.error("Ignite live state scan failed.", exception);
            throw exception;
        }
        return states;
    }

    @Override
    public void saveHomeState(LiveHomeState liveHomeState) {
        try {
            liveHomeStateCache.put(liveHomeState.homeId(), liveHomeState);
        } catch (RuntimeException exception) {
            log.error("Ignite live state write failed. homeId={}", liveHomeState.homeId(), exception);
            throw exception;
        }
    }
}
