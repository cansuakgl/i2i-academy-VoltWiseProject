package com.wattsmart.backend.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaErrorHandlingConfiguration {

    @Bean
    CommonErrorHandler commonKafkaErrorHandler() {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (record, exception) -> log.error(
                        "Kafka record handling failed after retries. topic={}, partition={}, offset={}, key={}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        record.key(),
                        exception),
                new FixedBackOff(1_000L, 2L));
        errorHandler.setRetryListeners((record, exception, deliveryAttempt) -> log.warn(
                "Kafka record handling retry. topic={}, partition={}, offset={}, key={}, attempt={}, message={}",
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                deliveryAttempt,
                exception.getMessage()));
        return errorHandler;
    }
}
