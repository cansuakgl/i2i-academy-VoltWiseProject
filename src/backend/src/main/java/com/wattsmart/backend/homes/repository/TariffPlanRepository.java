package com.wattsmart.backend.homes.repository;

import com.wattsmart.backend.homes.domain.TariffPlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TariffPlanRepository extends JpaRepository<TariffPlan, UUID> {

    List<TariffPlan> findAllByOrderByCodeAsc();

    Optional<TariffPlan> findByCode(String code);

    boolean existsByCode(String code);
}
