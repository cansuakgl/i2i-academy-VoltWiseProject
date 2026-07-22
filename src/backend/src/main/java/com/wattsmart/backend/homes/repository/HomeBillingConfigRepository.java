package com.wattsmart.backend.homes.repository;

import com.wattsmart.backend.homes.domain.HomeBillingConfig;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HomeBillingConfigRepository extends JpaRepository<HomeBillingConfig, UUID> {
}
