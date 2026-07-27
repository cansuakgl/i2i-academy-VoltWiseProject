package com.wattsmart.backend.telemetry.persistence.repository;

import com.wattsmart.backend.telemetry.persistence.domain.ApplianceAnomaly;
import com.wattsmart.backend.telemetry.persistence.domain.ApplianceAnomalyStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ApplianceAnomalyRepository extends JpaRepository<ApplianceAnomaly, UUID> {

    Optional<ApplianceAnomaly> findFirstByApplianceIdAndStatusOrderByStartedAtDesc(
            UUID applianceId,
            ApplianceAnomalyStatus status
    );

    List<ApplianceAnomaly> findByHomeIdOrderByStartedAtAsc(UUID homeId);

    @Query("""
            select anomaly
            from ApplianceAnomaly anomaly
            where anomaly.homeId = :homeId
              and anomaly.startedAt <= :to
              and (anomaly.resolvedAt is null or anomaly.resolvedAt >= :from)
            order by anomaly.startedAt asc
            """)
    List<ApplianceAnomaly> findOverlappingHomeAnomalies(UUID homeId, OffsetDateTime from, OffsetDateTime to);
}
