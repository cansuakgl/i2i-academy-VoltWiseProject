package com.wattsmart.backend.telemetry.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaApplianceTelemetryEventPublisher implements ApplianceTelemetryEventPublisher {

    private final KafkaTemplate<String, ApplianceTelemetryEvent> kafkaTemplate;

    @Value("${app.kafka.topics.appliance-telemetry}")
    private String topic;

    @Override
    public void publish(ApplianceTelemetryEvent event) {
        kafkaTemplate.send(topic, event.homeId().toString(), event)
                .whenComplete((result, exception) -> {
                    if (exception != null) {
                        log.error("Telemetry Kafka publish failed. topic={}, eventId={}, homeId={}",
                                topic,
                                event.eventId(),
                                event.homeId(),
                                exception);
                    } else {
                        log.info("Telemetry Kafka event published. topic={}, eventId={}, homeId={}, offset={}",
                                topic,
                                event.eventId(),
                                event.homeId(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
