package com.wattsmart.backend.homes.repository;

import com.wattsmart.backend.homes.domain.HomeUsageMonthlySummary;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HomeUsageMonthlySummaryRepository extends JpaRepository<HomeUsageMonthlySummary, UUID> {

    List<HomeUsageMonthlySummary> findByHomeIdAndMonthStartBetweenOrderByMonthStartAsc(UUID homeId, LocalDate from, LocalDate to);
}
