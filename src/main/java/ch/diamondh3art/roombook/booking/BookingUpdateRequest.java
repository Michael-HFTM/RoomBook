package ch.diamondh3art.roombook.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

// version = the version the client has read; a different current version means a lost update (A5)
public record BookingUpdateRequest(
        @NotNull OffsetDateTime startTime,
        @NotNull OffsetDateTime endTime,
        @NotBlank @Size(max = 200) String title,
        @NotNull Integer version) {
}
