package com.wattsmart.backend.homes.events;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpHomeRegistrationEventPublisher implements HomeRegistrationEventPublisher {

    @Override
    public void publish(HomeRegistrationEvent event) {
        log.info("Home registration event publishing skipped because Kafka is disabled. homeId={}", event.homeId());
    }
}
