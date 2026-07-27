package com.wattsmart.backend.telemetry.ingestion;

import com.wattsmart.backend.common.idempotency.IdempotencyService;
import com.wattsmart.backend.telemetry.events.ApplianceTelemetryEvent;
import com.wattsmart.backend.telemetry.service.TelemetryProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.telemetry-ingestion.enabled", havingValue = "true")
@lombok.RequiredArgsConstructor
public class TelemetryIngestionConsumer {

    private final IdempotencyService idempotencyService;
    private final TelemetryProcessingService telemetryProcessingService;

    @KafkaListener(
            topics = "${app.kafka.topics.appliance-telemetry}",
            groupId = "${spring.kafka.consumer.group-id}-telemetry-ingestion",
            autoStartup = "${app.kafka.listener.auto-startup}"
    )
    public void consume(ApplianceTelemetryEvent event) {
        try {
            String idempotencyKey = "telemetry:" + event.eventId();
            if (!idempotencyService.tryClaim(idempotencyKey)) {
                log.info("Skipped duplicate telemetry event. eventId={}, homeId={}", event.eventId(), event.homeId());
                return;
            }

            telemetryProcessingService.process(event);

            log.info("Consumed telemetry event. eventId={}, homeId={}, readings={}, aboveSafeLimit={}",
                    event.eventId(),
                    event.homeId(),
                    event.readings().size(),
                    event.readings().stream().filter(ApplianceTelemetryEvent.ApplianceReading::aboveSafeLimit).count());
        } catch (RuntimeException exception) {
            log.error("Telemetry Kafka consume failed. eventId={}, homeId={}",
                    event != null ? event.eventId() : null,
                    event != null ? event.homeId() : null,
                    exception);
            throw exception;
        }
    }
}
