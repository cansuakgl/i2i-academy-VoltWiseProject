package com.wattsmart.backend.homes.repository;

import com.wattsmart.backend.homes.domain.HomeBillingCycle;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HomeBillingCycleRepository extends JpaRepository<HomeBillingCycle, UUID> {

    List<HomeBillingCycle> findByHomeIdAndCycleStartedOnLessThanEqualAndCycleEndedOnGreaterThanEqualOrderByCycleStartedOnDesc(
            UUID homeId,
            LocalDate toDate,
            LocalDate fromDate
    );
}
