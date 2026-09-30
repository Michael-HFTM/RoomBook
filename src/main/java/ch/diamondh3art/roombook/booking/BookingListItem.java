package ch.diamondh3art.roombook.booking;

import java.time.OffsetDateTime;

// A6 list entry, filled directly by a JPQL constructor expression (T6) instead of loading entities
public record BookingListItem(Long id, Long roomId, String roomName, Long userId, String username,
                              OffsetDateTime startTime, OffsetDateTime endTime, String title, BookingStatus status) {
}
