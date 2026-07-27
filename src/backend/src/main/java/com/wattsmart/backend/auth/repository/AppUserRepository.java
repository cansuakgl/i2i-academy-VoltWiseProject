package com.wattsmart.backend.auth.repository;

import com.wattsmart.backend.auth.domain.AppUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    List<AppUser> findAllByOrderByFirstNameAscLastNameAsc();

    boolean existsByEmailIgnoreCase(String email);

    Optional<AppUser> findByEmailIgnoreCase(String email);
}
