package com.wattsmart.backend.telemetry.events;

public interface ApplianceTelemetryEventPublisher {

    void publish(ApplianceTelemetryEvent event);
}
