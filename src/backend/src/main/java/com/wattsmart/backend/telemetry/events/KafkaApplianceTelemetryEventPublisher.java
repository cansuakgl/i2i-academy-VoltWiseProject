package com.wattsmart.backend.telemetry.events;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaApplianceTelemetryEventPublisher implements ApplianceTelemetryEventPublisher {

    private final KafkaTemplate<String, ApplianceTelemetryEvent> kafkaTemplate;

    @Value("${app.kafka.topics.appliance-telemetry}")
    private String topic;

    @Override
    public void publish(ApplianceTelemetryEvent event) {
        kafkaTemplate.send(topic, event.homeId().toString(), event);
    }
}
