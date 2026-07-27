package com.wattsmart.backend.telemetry.persistence.repository;

import com.wattsmart.backend.homes.domain.UsagePercentageMilestone;
import com.wattsmart.backend.telemetry.persistence.domain.HomeMilestoneEvent;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HomeMilestoneEventRepository extends JpaRepository<HomeMilestoneEvent, UUID> {

    Optional<HomeMilestoneEvent> findFirstByHomeIdAndBillingCycleStartedOnAndMilestoneOrderByTriggeredAtDesc(
            UUID homeId,
            LocalDate billingCycleStartedOn,
            UsagePercentageMilestone milestone
    );

    List<HomeMilestoneEvent> findByHomeIdOrderByTriggeredAtAsc(UUID homeId);

    List<HomeMilestoneEvent> findByHomeIdAndTriggeredAtBetweenOrderByTriggeredAtAsc(
            UUID homeId,
            OffsetDateTime from,
            OffsetDateTime to
    );
}
