package com.wattsmart.backend.homes.repository;

import com.wattsmart.backend.homes.domain.HomeTariffPlan;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface HomeTariffPlanRepository extends JpaRepository<HomeTariffPlan, UUID> {

    @Query("""
            select homeTariffPlan
            from HomeTariffPlan homeTariffPlan
            join fetch homeTariffPlan.tariffPlan
            where homeTariffPlan.home.id = :homeId
              and homeTariffPlan.effectiveTo is null
            """)
    Optional<HomeTariffPlan> findByHomeId(UUID homeId);

    @Query("""
            select homeTariffPlan
            from HomeTariffPlan homeTariffPlan
            join fetch homeTariffPlan.tariffPlan
            where homeTariffPlan.home.id in :homeIds
              and homeTariffPlan.effectiveTo is null
            """)
    List<HomeTariffPlan> findByHomeIdIn(Collection<UUID> homeIds);

    @Query("""
            select homeTariffPlan
            from HomeTariffPlan homeTariffPlan
            join fetch homeTariffPlan.home
            join fetch homeTariffPlan.tariffPlan
            where homeTariffPlan.home.id = :homeId
            order by homeTariffPlan.effectiveFrom desc
            """)
    List<HomeTariffPlan> findHistoryByHomeId(UUID homeId);
}
