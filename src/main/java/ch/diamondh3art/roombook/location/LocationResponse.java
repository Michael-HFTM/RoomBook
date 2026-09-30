package ch.diamondh3art.roombook.location;

public record LocationResponse(Long id, Long parentId, String name, LocationType type, boolean active) {

    static LocationResponse from(Location location) {
        Location parent = location.getParent();
        return new LocationResponse(location.getId(), parent == null ? null : parent.getId(),
                location.getName(), location.getType(), location.isActive());
    }
}
