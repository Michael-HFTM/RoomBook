package ch.diamondh3art.roombook.booking;

import java.time.OffsetDateTime;

public record BookingResponse(Long id, Long roomId, Long userId, Long seriesId, OffsetDateTime startTime,
                              OffsetDateTime endTime, String title, BookingStatus status, int version) {

    static BookingResponse from(Booking booking) {
        BookingSeries series = booking.getSeries();
        return new BookingResponse(booking.getId(), booking.getRoom().getId(), booking.getUser().getId(),
                series == null ? null : series.getId(), booking.getStartTime(), booking.getEndTime(),
                booking.getTitle(), booking.getStatus(), booking.getVersion());
    }
}
