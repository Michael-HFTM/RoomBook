package ch.diamondh3art.roombook.room;

public record RoomResponse(Long id, Long locationId, String name, int capacity, boolean active) {

    static RoomResponse from(Room room) {
        return new RoomResponse(room.getId(), room.getLocation().getId(), room.getName(),
                room.getCapacity(), room.isActive());
    }
}
