package ch.diamondh3art.roombook.booking;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;

// A6 filter, all fields optional; from/to select bookings overlapping the period, locationId includes sub-levels
public record BookingFilter(
        Long roomId,
        Long userId,
        Long locationId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
        BookingStatus status) {
}
