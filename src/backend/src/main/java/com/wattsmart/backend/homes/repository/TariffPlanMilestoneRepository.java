package com.wattsmart.backend.homes.repository;

import com.wattsmart.backend.homes.domain.TariffPlanMilestone;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TariffPlanMilestoneRepository extends JpaRepository<TariffPlanMilestone, UUID> {

    List<TariffPlanMilestone> findByTariffPlanId(UUID tariffPlanId);

    @Modifying
    void deleteByTariffPlanId(UUID tariffPlanId);
}
