package com.wattsmart.backend.homes.repository;

import com.wattsmart.backend.homes.domain.Appliance;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

public interface ApplianceRepository extends JpaRepository<Appliance, UUID> {

    @Query("""
            SELECT appliance
            FROM Appliance appliance
            JOIN FETCH appliance.home
            JOIN FETCH appliance.applianceTypeProfile
            WHERE appliance.active = true
            """)
    List<Appliance> findActiveAppliancesForSimulation();

    @Query("""
            SELECT appliance
            FROM Appliance appliance
            JOIN FETCH appliance.applianceTypeProfile
            WHERE appliance.home.id IN :homeIds
            ORDER BY appliance.displayOrder ASC, appliance.name ASC
            """)
    List<Appliance> findStatusAppliancesByHomeIds(@Param("homeIds") Collection<UUID> homeIds);
}
