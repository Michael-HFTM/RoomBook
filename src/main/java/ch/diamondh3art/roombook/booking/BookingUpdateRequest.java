package ch.diamondh3art.roombook.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

// version = the version the client has read; a different current version means a lost update (A5)
public record BookingUpdateRequest(
        @Schema(example = "2026-10-05T11:00:00+02:00", description = "ISO-8601 with offset, e.g. +02:00 (summer) or +01:00 (winter) for Zurich local time")
        @NotNull OffsetDateTime startTime,
        @Schema(example = "2026-10-05T12:00:00+02:00", description = "ISO-8601 with offset, e.g. +02:00 (summer) or +01:00 (winter) for Zurich local time")
        @NotNull OffsetDateTime endTime,
        @NotBlank @Size(max = 200) String title,
        @NotNull Integer version) {
}
