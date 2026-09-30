package ch.diamondh3art.roombook.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {

    /*
     * A3: active rooms without an overlapping active booking. The CTE walks the hierarchy from the roots through
     * active locations only, so rooms below a deactivated location are excluded; in_scope marks the subtree of
     * :locationId (all locations if null). The overlap test matches ex_booking_room_id and can use its GiST index.
     */
    @Query(value = """
            WITH RECURSIVE active_location (location_id, in_scope) AS (
                SELECT location_id, CAST(:locationId AS bigint) IS NULL OR location_id = :locationId
                FROM location
                WHERE parent_location_id IS NULL AND active
                UNION ALL
                SELECT l.location_id, a.in_scope OR l.location_id = :locationId
                FROM location l
                         JOIN active_location a ON l.parent_location_id = a.location_id
                WHERE l.active)
            SELECT r.*
            FROM room r
                     JOIN active_location a ON a.location_id = r.location_id AND a.in_scope
            WHERE r.active
              AND r.capacity >= :minCapacity
              AND NOT EXISTS (SELECT 1
                              FROM booking b
                              WHERE b.room_id = r.room_id
                                AND b.status <> 'CANCELLED'
                                AND tstzrange(b.start_time, b.end_time)
                                    && tstzrange(CAST(:from AS timestamptz), CAST(:to AS timestamptz)))
            ORDER BY r.capacity, r.name, r.room_id""", nativeQuery = true)
    List<Room> findFree(Long locationId, int minCapacity, OffsetDateTime from, OffsetDateTime to);
}
