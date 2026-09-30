package ch.diamondh3art.roombook.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

// occurrences null = single booking, otherwise a weekly series (A4)
public record BookingRequest(
        @NotNull Long roomId,
        @Schema(example = "2026-10-05T09:00:00+02:00", description = "ISO-8601 with offset, e.g. +02:00 (summer) or +01:00 (winter) for Zurich local time")
        @NotNull OffsetDateTime startTime,
        @Schema(example = "2026-10-05T10:00:00+02:00", description = "ISO-8601 with offset, e.g. +02:00 (summer) or +01:00 (winter) for Zurich local time")
        @NotNull OffsetDateTime endTime,
        @NotBlank @Size(max = 200) String title,
        @Min(1) @Max(12) Integer occurrences) {
}
