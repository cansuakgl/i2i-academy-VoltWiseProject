package com.wattsmart.backend.homes.repository;

import com.wattsmart.backend.homes.domain.HomeUsageDaily;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HomeUsageDailyRepository extends JpaRepository<HomeUsageDaily, Long> {

    List<HomeUsageDaily> findByHomeIdAndUsageDateBetweenOrderByUsageDateAsc(UUID homeId, LocalDate from, LocalDate to);
}
