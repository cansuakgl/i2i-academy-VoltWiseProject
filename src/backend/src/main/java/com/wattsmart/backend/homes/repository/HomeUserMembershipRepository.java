package com.wattsmart.backend.homes.repository;

import com.wattsmart.backend.homes.domain.HomeUserMembership;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface HomeUserMembershipRepository extends JpaRepository<HomeUserMembership, UUID> {

    @Query("""
            select membership
            from HomeUserMembership membership
            join fetch membership.home
            where membership.user.id = :userId
              and membership.acceptedAt is not null
            order by membership.home.name asc
            """)
    List<HomeUserMembership> findAcceptedMembershipsByUserId(UUID userId);

    boolean existsByHomeIdAndUserIdAndAcceptedAtIsNotNull(UUID homeId, UUID userId);

    boolean existsByHomeIdAndUserId(UUID homeId, UUID userId);

    Optional<HomeUserMembership> findByHomeIdAndUserId(UUID homeId, UUID userId);

    @Query("""
            select membership.home.id
            from HomeUserMembership membership
            where membership.user.id = :userId
              and membership.acceptedAt is not null
            order by membership.home.name asc
            """)
    List<UUID> findAcceptedHomeIdsByUserId(UUID userId);

    @Query("""
            select membership
            from HomeUserMembership membership
            join fetch membership.user
            where membership.home.id = :homeId
            order by membership.user.firstName asc, membership.user.lastName asc
            """)
    List<HomeUserMembership> findMembersByHomeId(UUID homeId);
}
