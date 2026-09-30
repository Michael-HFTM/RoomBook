package ch.diamondh3art.roombook.room;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RoomRequest(
        @NotNull Long locationId,
        @NotBlank @Size(max = 100) String name,
        @NotNull @Positive Integer capacity) {
}
