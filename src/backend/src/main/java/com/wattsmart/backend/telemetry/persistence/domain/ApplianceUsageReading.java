package com.wattsmart.backend.telemetry.persistence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "appliance_usage_readings", schema = "wattsmart")
public class ApplianceUsageReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "home_id", nullable = false)
    private java.util.UUID homeId;

    @Column(name = "appliance_id", nullable = false)
    private java.util.UUID applianceId;

    @Column(name = "reading_window_started_at", nullable = false)
    private OffsetDateTime readingWindowStartedAt;

    @Column(name = "reading_window_ended_at", nullable = false)
    private OffsetDateTime readingWindowEndedAt;

    @Column(name = "average_watts", nullable = false)
    private BigDecimal averageWatts;

    @Column(name = "peak_watts")
    private BigDecimal peakWatts;

    @Column(name = "energy_kwh", nullable = false)
    private BigDecimal energyKwh;

    @Column(name = "sample_count", nullable = false)
    private int sampleCount;
}
