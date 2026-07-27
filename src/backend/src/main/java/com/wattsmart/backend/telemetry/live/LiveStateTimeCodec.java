package com.wattsmart.backend.telemetry.live;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public final class LiveStateTimeCodec {

    private LiveStateTimeCodec() {
    }

    public static String toIso(LocalDate value) {
        return value != null ? value.toString() : null;
    }

    public static String toIso(OffsetDateTime value) {
        return value != null ? value.toString() : null;
    }

    public static LocalDate toLocalDate(String value) {
        return value != null ? LocalDate.parse(value) : null;
    }

    public static OffsetDateTime toOffsetDateTime(String value) {
        return value != null ? OffsetDateTime.parse(value) : null;
    }
}
