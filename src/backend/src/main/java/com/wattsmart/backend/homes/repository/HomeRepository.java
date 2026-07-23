package com.wattsmart.backend.homes.repository;

import com.wattsmart.backend.homes.domain.Home;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HomeRepository extends JpaRepository<Home, UUID> {

    boolean existsByExternalKey(String externalKey);

    List<Home> findAllByOrderByNameAsc();
}
