package com.wattsmart.backend.homes.repository;

import com.wattsmart.backend.homes.domain.HomeUserMembership;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HomeUserMembershipRepository extends JpaRepository<HomeUserMembership, UUID> {
}
