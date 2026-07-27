package com.wattsmart.backend.homes.repository;

import com.wattsmart.backend.homes.domain.ApplianceModelProfile;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ApplianceModelProfileRepository extends JpaRepository<ApplianceModelProfile, UUID> {

    @Query("""
            select profile
            from ApplianceModelProfile profile
            join fetch profile.applianceType
            order by lower(profile.manufacturer), lower(profile.modelName)
            """)
    List<ApplianceModelProfile> findAllWithApplianceType();
}
