package com.wattsmart.backend.homes.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaHomeRegistrationEventPublisher implements HomeRegistrationEventPublisher {

    private final KafkaTemplate<String, HomeRegistrationEvent> kafkaTemplate;

    @Value("${app.kafka.topics.home-registration}")
    private String topic;

    @Override
    public void publish(HomeRegistrationEvent event) {
        kafkaTemplate.send(topic, event.homeId().toString(), event)
                .whenComplete((result, exception) -> {
                    if (exception != null) {
                        log.error("Home registration Kafka publish failed. topic={}, homeId={}", topic, event.homeId(), exception);
                    } else {
                        log.info("Home registration Kafka event published. topic={}, homeId={}, offset={}",
                                topic,
                                event.homeId(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
