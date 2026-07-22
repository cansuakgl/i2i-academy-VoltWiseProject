package com.wattsmart.backend.homes.repository;

import com.wattsmart.backend.homes.domain.ApplianceTypeProfile;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplianceTypeProfileRepository extends JpaRepository<ApplianceTypeProfile, UUID> {

    List<ApplianceTypeProfile> findByCodeIn(Collection<String> codes);
}
