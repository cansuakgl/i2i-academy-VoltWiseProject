package com.wattsmart.backend.homes.events;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnMissingBean(HomeRegistrationEventPublisher.class)
public class NoOpHomeRegistrationEventPublisher implements HomeRegistrationEventPublisher {

    @Override
    public void publish(HomeRegistrationEvent event) {
        log.info("Home registration event publishing skipped because Kafka is disabled. homeId={}", event.homeId());
    }
}
