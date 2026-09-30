package ch.diamondh3art.roombook.booking;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

// occurrences null = single booking, otherwise a weekly series (A4)
public record BookingRequest(
        @NotNull Long roomId,
        @NotNull OffsetDateTime startTime,
        @NotNull OffsetDateTime endTime,
        @NotBlank @Size(max = 200) String title,
        @Min(1) @Max(12) Integer occurrences) {
}
