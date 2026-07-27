package com.wattsmart.backend.homes.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record HomeAnomalyHistoryResponse(
        UUID homeId,
        LocalDate fromDate,
        LocalDate toDate,
        List<HomeHistoryResponse.ApplianceAnomalyItem> applianceAnomalies
) {
}
