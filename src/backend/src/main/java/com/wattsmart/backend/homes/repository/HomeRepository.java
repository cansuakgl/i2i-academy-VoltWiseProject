package com.wattsmart.backend.homes.repository;

import com.wattsmart.backend.homes.domain.Home;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface HomeRepository extends JpaRepository<Home, UUID> {

    boolean existsByExternalKey(String externalKey);

    List<Home> findAllByOrderByNameAsc();

    @Query("select home.id from Home home order by home.name asc")
    List<UUID> findAllIds();
}
