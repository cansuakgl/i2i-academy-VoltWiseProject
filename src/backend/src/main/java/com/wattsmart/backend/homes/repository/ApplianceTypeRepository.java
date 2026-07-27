package com.wattsmart.backend.homes.repository;

import com.wattsmart.backend.homes.domain.ApplianceType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplianceTypeRepository extends JpaRepository<ApplianceType, UUID> {

    Optional<ApplianceType> findByCode(String code);

    List<ApplianceType> findByCodeIn(Collection<String> codes);
}
