package com.wattsmart.backend.homes.repository;

import com.wattsmart.backend.homes.domain.HomeBillingAccount;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HomeBillingAccountRepository extends JpaRepository<HomeBillingAccount, UUID> {

    List<HomeBillingAccount> findByHomeIdIn(Collection<UUID> homeIds);

    Optional<HomeBillingAccount> findByHomeId(UUID homeId);
}
