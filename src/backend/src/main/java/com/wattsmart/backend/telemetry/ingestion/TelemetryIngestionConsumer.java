package com.wattsmart.backend.telemetry.ingestion;

import com.wattsmart.backend.telemetry.events.ApplianceTelemetryEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.telemetry-ingestion.enabled", havingValue = "true")
public class TelemetryIngestionConsumer {

    @KafkaListener(
            topics = "${app.kafka.topics.appliance-telemetry}",
            groupId = "${spring.kafka.consumer.group-id}-telemetry-ingestion",
            autoStartup = "${app.kafka.listener.auto-startup}"
    )
    public void consume(ApplianceTelemetryEvent event) {
        long anomalyCount = event.readings().stream()
                .filter(ApplianceTelemetryEvent.ApplianceReading::aboveSafeLimit)
                .count();

        log.info("Consumed telemetry event. homeId={}, readings={}, aboveSafeLimit={}",
                event.homeId(),
                event.readings().size(),
                anomalyCount);
    }
}
