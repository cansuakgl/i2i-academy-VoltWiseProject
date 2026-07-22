package com.wattsmart.backend.homes.repository;

import com.wattsmart.backend.homes.domain.HomeBillingAccount;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HomeBillingAccountRepository extends JpaRepository<HomeBillingAccount, UUID> {
}
