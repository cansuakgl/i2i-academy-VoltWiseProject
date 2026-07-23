package com.wattsmart.backend.telemetry.events;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnMissingBean(ApplianceTelemetryEventPublisher.class)
public class NoOpApplianceTelemetryEventPublisher implements ApplianceTelemetryEventPublisher {

    @Override
    public void publish(ApplianceTelemetryEvent event) {
        log.info("Telemetry publishing skipped because Kafka is disabled. homeId={}, readings={}",
                event.homeId(),
                event.readings().size());
    }
}
