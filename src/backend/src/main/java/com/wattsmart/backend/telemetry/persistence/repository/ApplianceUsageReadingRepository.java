package com.wattsmart.backend.telemetry.persistence.repository;

import com.wattsmart.backend.telemetry.persistence.domain.ApplianceUsageReading;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplianceUsageReadingRepository extends JpaRepository<ApplianceUsageReading, Long> {

    Optional<ApplianceUsageReading> findByApplianceIdAndReadingWindowStartedAtAndReadingWindowEndedAt(
            UUID applianceId,
            OffsetDateTime readingWindowStartedAt,
            OffsetDateTime readingWindowEndedAt
    );
}
