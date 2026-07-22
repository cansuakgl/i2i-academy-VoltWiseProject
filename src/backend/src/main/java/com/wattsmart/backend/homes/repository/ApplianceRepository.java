package com.wattsmart.backend.homes.repository;

import com.wattsmart.backend.homes.domain.Appliance;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplianceRepository extends JpaRepository<Appliance, UUID> {
}
