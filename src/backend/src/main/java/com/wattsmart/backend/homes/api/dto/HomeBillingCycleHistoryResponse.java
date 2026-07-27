package com.wattsmart.backend.homes.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record HomeBillingCycleHistoryResponse(
        UUID homeId,
        LocalDate fromDate,
        LocalDate toDate,
        List<HomeHistoryResponse.BillingCycleItem> billingCycles
) {
}
