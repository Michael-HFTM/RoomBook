package ch.diamondh3art.roombook.booking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Collection;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /*
     * A6 (T6, T7): DTO projection with one query per page, filtered and paged in the DB. ORDER BY start_time, id is
     * part of the query so the order stays stable for equal start times, independent of the Pageable.
     * The casts type the time parameters, otherwise PostgreSQL rejects "? IS NULL" for a null value.
     * ponytail: optional filters as ":param IS NULL OR ..." keep one static query; switch to a dynamic query if
     * the T10 measurement shows a bad generic plan.
     */
    @SuppressWarnings("JpaQlInspection") // IntelliJ only knows JPA cast targets, Hibernate also accepts OffsetDateTime
    @Query("""
            SELECT new ch.diamondh3art.roombook.booking.BookingListItem(
                b.id, r.id, r.name, u.id, u.username, b.startTime, b.endTime, b.title, b.status)
            FROM Booking b
                JOIN b.room r
                JOIN b.user u
            WHERE (:roomId IS NULL OR r.id = :roomId)
              AND (:userId IS NULL OR u.id = :userId)
              AND (:allLocations = true OR r.location.id IN :locationIds)
              AND (CAST(:from AS OffsetDateTime) IS NULL OR b.endTime > :from)
              AND (CAST(:to AS OffsetDateTime) IS NULL OR b.startTime < :to)
              AND (:status IS NULL OR b.status = :status)
            ORDER BY b.startTime, b.id""")
    Page<BookingListItem> search(Long roomId, Long userId, boolean allLocations, Collection<Long> locationIds,
                                 OffsetDateTime from, OffsetDateTime to, BookingStatus status, Pageable pageable);
}
