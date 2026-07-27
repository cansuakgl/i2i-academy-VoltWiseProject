package com.wattsmart.backend.telemetry.live;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface LiveHomeStateStore {

    LiveHomeState getHomeState(UUID homeId);

    Map<UUID, LiveHomeState> getHomeStates(Collection<UUID> homeIds);

    Map<UUID, LiveHomeState> getAllHomeStates();

    void saveHomeState(LiveHomeState liveHomeState);
}
