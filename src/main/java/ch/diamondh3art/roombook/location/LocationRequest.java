package ch.diamondh3art.roombook.location;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// parentId null = root node
public record LocationRequest(
        Long parentId,
        @NotBlank @Size(max = 100) String name,
        @NotNull LocationType type) {
}
