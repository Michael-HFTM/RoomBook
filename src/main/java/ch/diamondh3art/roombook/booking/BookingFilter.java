package ch.diamondh3art.roombook.booking;

import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;

// A6 filter, all fields optional; from/to select bookings overlapping the period, locationId includes sub-levels
public record BookingFilter(
        Long roomId,
        Long userId,
        Long locationId,
        @Parameter(example = "2026-10-01T00:00:00+02:00", description = "ISO-8601 with offset, e.g. +02:00 (summer) or +01:00 (winter) for Zurich local time")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @Parameter(example = "2026-11-01T00:00:00+01:00", description = "ISO-8601 with offset, e.g. +02:00 (summer) or +01:00 (winter) for Zurich local time")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
        BookingStatus status) {
}
