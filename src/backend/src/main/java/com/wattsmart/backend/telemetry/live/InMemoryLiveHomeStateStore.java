package com.wattsmart.backend.telemetry.live;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.live-state.store", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryLiveHomeStateStore implements LiveHomeStateStore {

    private final Map<UUID, LiveHomeState> statesByHomeId = new ConcurrentHashMap<>();

    @Override
    public LiveHomeState getHomeState(UUID homeId) {
        return statesByHomeId.get(homeId);
    }

    @Override
    public Map<UUID, LiveHomeState> getHomeStates(Collection<UUID> homeIds) {
        return homeIds.stream()
                .filter(statesByHomeId::containsKey)
                .collect(Collectors.toMap(homeId -> homeId, statesByHomeId::get));
    }

    @Override
    public Map<UUID, LiveHomeState> getAllHomeStates() {
        return new HashMap<>(statesByHomeId);
    }

    @Override
    public void saveHomeState(LiveHomeState liveHomeState) {
        statesByHomeId.put(liveHomeState.homeId(), liveHomeState);
    }
}
